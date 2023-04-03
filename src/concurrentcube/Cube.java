package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private static final int NUMBER_OF_SIDES = 6;
    // Oś nr 3 jest sztuczna, jest po to, aby utrzymywać nr grupy dla Show.
    private static final int NUMBER_OF_AXISES = 4;
    private static final int top = 0;
    private static final int left = 1;
    private static final int front = 2;
    private static final int right = 3;
    private static final int back = 4;
    private static final int bottom = 5;
    private static final int[][] axis = new int[NUMBER_OF_AXISES - 1][2];
    private static final int show_group_id = 3;
    private static final int MAX_WORKING_PROCESSES = 100;

    private final int size;
    private final int[][][] fields;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    // Semafor do ochrony zmiennych globalnych.
    private final Semaphore mutex = new Semaphore(1, true);

    // Semafor, na którym czekają reprezentanci procesów z każdej osi.
    private final Semaphore first_process = new Semaphore(0, true);

    // Semafory dbające, aby dokładnie 1 proces obracał konkretną warstwę.
    private final Semaphore[] layers;

    // Semafory, na których procesy inne niż reprezentant czekają na możliwość pracy.
    private final Semaphore[] group;

    // Zmienna przechowująca ilość procesów z danej grupy, które rozpoczęły pracę.
    private int how_many_entered = 0;

    // Zmienna przechowująca informacje o obecnie pracującej grupie procesów.
    // -1, jeśli nie pracuje żadna grupa.
    private int working_axis = -1;

    // Zmienna przechowująca ilość procesów, które w danej chwili pracują
    // lub czekają na semaforze layers.
    private int working_processes = 0;

    // Zmienna przechowująca informacje o ilościach procesów z każdej grupy,
    // które czekają na możliwość rozpoczęcia pracy.
    private final int[] waiting_in_group;

    // Zmienna przechowująca informację o ilości grup (liczbie ich reprezentantów),
    // które czekają na możliwość rozpoczęcia pracy.
    private int waiting_groups = 0;

    // W tablicy axis przechowywane są informacje o ściankach w każdej osi.
    static {
        axis[0][0] = left;
        axis[0][1] = right;
        axis[1][0] = front;
        axis[1][1] = back;
        axis[2][0] = top;
        axis[2][1] = bottom;
    }

    public Cube(int size,
                BiConsumer<Integer, Integer>beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.fields = new int[NUMBER_OF_SIDES][size][size];
        for (int side = 0; side < NUMBER_OF_SIDES; side++) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    fields[side][row][column] = side;
                }
            }
        }
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;
        waiting_in_group = new int[NUMBER_OF_AXISES];
        group = new Semaphore[NUMBER_OF_AXISES];
        layers = new Semaphore[size];
        for (int i = 0; i < NUMBER_OF_AXISES; i++) {
            waiting_in_group[i] = 0;
            group[i] = new Semaphore(0);
        }
        for (int i = 0; i < size; i++) {
            layers[i] = new Semaphore(1);
        }
    }

    // Funkcja przywracająca kostkę do stanu początkowego.
    public void resetFields() {
        for (int i = 0; i < NUMBER_OF_SIDES; i++) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    fields[i][row][column] = i;
                }
            }
        }
    }

    // Funkcja licząca kwadraty w każdym kolorze zwraca true,
    // jeśli ilość kwadratów w każdym kolorze wynosi rozmiar_kostki^2. Wpp. false.
    public boolean correctCountOfNumbers() {
        int[] result = new int[NUMBER_OF_SIDES];
        for (int i = 0; i < NUMBER_OF_SIDES; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    int temp = fields[i][j][k];
                    result[temp]++;
                }
            }
        }
        for (int i = 0; i < NUMBER_OF_SIDES; i++) if (result[i] != size * size) return false;
        return true;
    }

    // Funkcja dbająca o poprawny obrót warstw
    // brzegowych tzn. takich, że (layer == 0 or layer == size - 1).
    private void rotateWhenLayerIsEdgy(int side, int layer) {
        if (layer != 0 && layer != size - 1) return;
        int[][] temp_side = new int[size][size];
        int side_to_rotate = side;

        if (layer == size - 1) {
            int ax = ((side + 2) % 5) % 3;
            if (axis[ax][0] == side) side_to_rotate = axis[ax][1];
            else side_to_rotate = axis[ax][0];
        }

        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                if (layer == 0) temp_side[row][column] = fields[side_to_rotate][size - column - 1][row];
                else temp_side[row][column] = fields[side_to_rotate][column][size - row - 1];
            }
        }

        for (int row = 0; row < size; row++) {
            System.arraycopy(temp_side[row], 0, fields[side_to_rotate][row], 0, size);
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Front.
    private void rotateFront(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][size - layer - 1][i];
            fields[top][size - layer - 1][i] = fields[left][size - i - 1][size - layer - 1];
            int temp_square2 = fields[right][i][layer];
            fields[right][i][layer] = temp_square1;
            temp_square1 = fields[bottom][layer][size - i - 1];
            fields[bottom][layer][size - i - 1] = temp_square2;
            fields[left][size - i - 1][size - layer - 1] = temp_square1;
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Back.
    private void rotateBack(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][layer][size - i - 1];
            fields[top][layer][size - i - 1] = fields[right][size - i - 1][size - layer - 1];
            int temp_square2 = fields[left][i][layer];
            fields[left][i][layer] = temp_square1;
            temp_square1 = fields[bottom][size - layer - 1][i];
            fields[bottom][size - layer - 1][i] = temp_square2;
            fields[right][size - i - 1][size - layer - 1] = temp_square1;
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Top.
    private void rotateTop(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[back][layer][size - i - 1];
            fields[back][layer][size - i - 1] = fields[left][layer][size - i - 1];
            int temp_square2 = fields[right][layer][size - i - 1];
            fields[right][layer][size - i - 1] = temp_square1;
            temp_square1 = fields[front][layer][size - i - 1];
            fields[front][layer][size - i - 1] = temp_square2;
            fields[left][layer][size - i - 1] = temp_square1;
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Bottom.
    private void rotateBottom(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[front][size - layer - 1][i];
            fields[front][size - layer - 1][i] = fields[left][size - layer - 1][i];
            int temp_square2 = fields[right][size - layer - 1][i];
            fields[right][size - layer - 1][i] = temp_square1;
            temp_square1 = fields[back][size - layer - 1][i];
            fields[back][size - layer - 1][i] = temp_square2;
            fields[left][size - layer - 1][i] = temp_square1;
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Left.
    private void rotateLeft(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][i][layer];
            fields[top][i][layer] = fields[back][size - i - 1][size - layer - 1];
            int temp_square2 = fields[front][i][layer];
            fields[front][i][layer] = temp_square1;
            temp_square1 = fields[bottom][i][layer];
            fields[bottom][i][layer] = temp_square2;
            fields[back][size - i - 1][size - layer - 1] = temp_square1;
        }
    }

    // Funkcja odpowiadająca za pojedynczy obrót warstwy patrząc od strony Right.
    private void rotateRight(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][size - i - 1][size - layer - 1];
            fields[top][size - i - 1][size - layer - 1] = fields[front][size - i - 1][size - layer - 1];
            int temp_square2 = fields[back][i][layer];
            fields[back][i][layer] = temp_square1;
            temp_square1 = fields[bottom][size - i - 1][size - layer - 1];
            fields[bottom][size - i - 1][size - layer - 1] = temp_square2;
            fields[front][size - i - 1][size - layer - 1] = temp_square1;
        }
    }

    private void preliminaryProtocol(int my_axis) throws InterruptedException {
        mutex.acquireUninterruptibly(); // Będziemy potrzebować mutexa, aby modyfikować zmienne globalne.
        if (working_axis != my_axis && working_axis != -1 || (working_axis == my_axis &&
            (how_many_entered < MAX_WORKING_PROCESSES || waiting_groups == 0))) {

            // Nie mogliśmy wejść do elsa, więc czekamy.
            waiting_in_group[my_axis]++;
            // Jeśli jesteśmy pierwszym procesem z grupy, to czekamy jako reprezentant.
            if (waiting_in_group[my_axis] == 1) {
                waiting_groups++;
                mutex.release();
                try {
                    first_process.acquire(); // Jak ktoś nas wpuści, to dziedziczymy SK.
                }
                catch (InterruptedException e) {
                    mutex.acquireUninterruptibly();
                    waiting_groups--;
                    waiting_in_group[my_axis]--;
                    mutex.release();
                    throw e;
                }
                waiting_groups--;
                how_many_entered++;
                working_axis = my_axis;
            }
            // Czekamy, aż proces z naszej grupy nas wpuści.
            else {
                mutex.release();
                try {
                    group[my_axis].acquire(); // Dziedziczymy SK.
                }
                catch (InterruptedException e) {
                    mutex.acquireUninterruptibly();
                    waiting_in_group[my_axis]--;
                    mutex.release();
                    throw e;
                }
            }
            // Skoro nie czekamy, to aktualizujemy stan systemu.
            waiting_in_group[my_axis]--;
            working_processes++;
            // Wpuszczamy pozostałe procesy z naszej grupy kaskadowo.
            // My wpuszczamy 1 proces, on wpuszcza kolejny itd.
            //, aż któryś z warunków będzie fałszywy.
            if (waiting_in_group[my_axis] > 0 &&
                    (how_many_entered < MAX_WORKING_PROCESSES || waiting_groups == 0)) {
                group[my_axis].release();
            }
            else mutex.release();
        }
        // Jeśli nikt nie pracuje lub pracuje grupa - my_axis oraz nie
        // rozpoczęło pracy MAX_WORKING_PROCESSES procesow z tej grupy lub
        // żadna inna grupa nie czeka
        else {
            how_many_entered++;
            working_axis = my_axis;
            working_processes++;
            mutex.release();
        }
    }

    private void finalProtocol() throws InterruptedException {
        mutex.acquireUninterruptibly();
        // Proces kończy pracę, więc aktualizujemy stan systemu.
        working_processes--;
        // Jeśli jest ostatnim procesem, który pracował, to
        // resetujemy wartość zmiennej how_many_entered.
        if (working_processes == 0) {
            how_many_entered = 0;
            // Jeśli ktoś czeka, wpuszczamy go.
            if (waiting_groups > 0) first_process.release(); // Oddajemy SK.
            // Wpp. nikt nie pracuje.
            else {
                working_axis = -1;
                mutex.release();
            }
        }
        else mutex.release();
    }

    public void rotate(int side, int layer) throws InterruptedException {
        // Sprawdzamy poprawność parametrów.
        assert (side >= 0 && side < NUMBER_OF_SIDES && layer >= 0 && layer < size);
        // Wyliczamy nr osi.
        int my_axis = ((side + 2) % 5) % 3;

        preliminaryProtocol(my_axis);

        try {
            // Ustalamy, który semafor próbujemy opuścić.
            if (side == axis[my_axis][0]) layers[layer].acquire();
            else layers[size - layer - 1].acquire();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Musimy wykonać protokół końcowy, aby ustawić dobrze zmienne.
            finalProtocol();
            throw e;
        }

        beforeRotation.accept(side, layer);

        rotateWhenLayerIsEdgy(side, layer);
        if (side == front) rotateFront(layer);
        else if (side == back) rotateBack(layer);
        else if (side == top) rotateTop(layer);
        else if (side == bottom) rotateBottom(layer);
        else if (side == left) rotateLeft(layer);
        else rotateRight(layer);

        afterRotation.accept(side,layer);

        // Ustalamy, który semafor podnieść.
        if (side == axis[my_axis][0]) layers[layer].release();
        else layers[size - layer - 1].release();

        finalProtocol();
    }

    public String show() throws InterruptedException {
        preliminaryProtocol(show_group_id);
        beforeShowing.run();
        StringBuilder result = new StringBuilder();
        for (int side = 0; side < NUMBER_OF_SIDES; side++) {
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    result.append(fields[side][row][column]);
                }
            }
        }
        afterShowing.run();
        finalProtocol();
        return result.toString();
    }
}

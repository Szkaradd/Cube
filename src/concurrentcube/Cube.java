package concurrentcube;

import utils.Shower;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private static final int NUMBER_OF_SIDES = 6;
    private static final int NUMBER_OF_AXISES = 3;
    private final int size;
    private int[][][] fields;
    private static final int top = 0;
    private static final int left = 1;
    private static final int front = 2;
    private static final int right = 3;
    private static final int back = 4;
    private static final int bottom = 5;
    private static final int[][] axis = new int[NUMBER_OF_AXISES][2];
    private final BiConsumer<Integer, Integer>beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private final Semaphore mutex = new Semaphore(1);
    private final Semaphore first_process = new Semaphore(1);
    private final Semaphore end_synchronizer = new Semaphore(0);
    private final Semaphore[] layers;
    private final Semaphore[] group;
    private int working_axis = -1;
    private int working_processes = 0;
    private int[] waiting_in_group;
    private int waiting_to_leave = 0;
    private int waiting_groups = 0;

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

    public int[] count_numbers() {
        int[] result = new int[NUMBER_OF_SIDES];
        for (int i = 0; i < NUMBER_OF_SIDES; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    int temp = fields[i][j][k];
                    result[temp]++;
                }
            }
        }
        for (int i = 0; i < NUMBER_OF_SIDES; i++) System.out.println(result[i]);
        return result;
    }

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

    private void rotateFront(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][size - layer - 1][i]; // pamietamy gore
            fields[top][size - layer - 1][i] = fields[left][size - i - 1][size - layer - 1]; // ustawiamy gore
            int temp_square2 = fields[right][i][layer]; // pamietamy prawo
            fields[right][i][layer] = temp_square1; // ustawiamy prawo
            temp_square1 = fields[bottom][layer][size - i - 1]; //pamietamy dol
            fields[bottom][layer][size - i - 1] = temp_square2; // ustawiamy dol
            fields[left][size - i - 1][size - layer - 1] = temp_square1; // ustawiamy lewo
        }
    }

    private void rotateBack(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[top][layer][size - i - 1]; // pamietamy gore
            fields[top][layer][size - i - 1] = fields[right][size - i - 1][size - layer - 1]; //ustawiamy gore
            int temp_square2 = fields[left][i][layer]; // pamietamy lewo
            fields[left][i][layer] = temp_square1; // ustawiamy lewo
            temp_square1 = fields[bottom][size - layer - 1][i]; // pamietamy dol
            fields[bottom][size - layer - 1][i] = temp_square2; //ustawiamy dol
            fields[right][size - i - 1][size - layer - 1] = temp_square1;// ustawiamy prawo
        }
    }

    private void rotateTop(int layer) {
        for (int i = 0; i < size; i++) {
            int temp_square1 = fields[back][layer][size - i - 1];  // pamietamy tył
            fields[back][layer][size - i - 1] = fields[left][layer][size - i - 1]; // ustawiamy tył
            int temp_square2 = fields[right][layer][size - i - 1]; // pamietamy prawo
            fields[right][layer][size - i - 1] = temp_square1; // ustawiamy prawo
            temp_square1 = fields[front][layer][size - i - 1]; // pamietamy przod
            fields[front][layer][size - i - 1] = temp_square2; //ustawiamy dol
            fields[left][layer][size - i - 1] = temp_square1;  //ustawiamy lewo
        }
    }

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

    private void preliminary_protocol(int my_axis, int side, int layer) throws InterruptedException {
        mutex.acquire();
        if (working_axis == -1)
            working_axis = my_axis;
        else {
            if (working_axis != my_axis) {
                waiting_in_group[my_axis]++;
                if (waiting_in_group[my_axis] == 1) {
                    waiting_groups++;
                    mutex.release();
                    first_process.acquire();
                    waiting_groups--;
                    working_axis = my_axis;
                }
                else {
                    mutex.release();
                    group[my_axis].acquire();
                }
                waiting_in_group[my_axis]--;
            }
        }
        working_processes++;
        //System.out.println(working_processes + " " + side + " " + layer);
        if (waiting_in_group[my_axis] > 0) group[my_axis].release();
        else mutex.release();
    }

    private void final_protocol() throws InterruptedException {
        mutex.acquire();
        //System.out.println("Mam mutex");
        working_processes--;
        if (working_processes > 0) {
            waiting_to_leave++;
            mutex.release();
            end_synchronizer.acquire();
            waiting_to_leave--;
        }
        if (waiting_to_leave > 0) end_synchronizer.release();
        else {
            if (waiting_groups > 0) first_process.release();
            else {
                working_axis = -1;
                mutex.release();
            }
        }
    }

    public void rotate(int side, int layer) throws InterruptedException {
        //System.out.println("I will rotate (" + side + ", " + layer + ")");
        assert (side >= 0 && side <= 5 && layer >= 0 && layer < size);
        int my_axis = ((side + 2) % 5) % 3;

        preliminary_protocol(my_axis, side, layer);

        //System.out.println("Nie zablokowałem sie we wstepnym :)");

        if (side == axis[my_axis][0]) layers[layer].acquire();
        else layers[size - layer - 1].acquire();

        //System.out.println("Moze tu sie blokuje?");

        beforeRotation.accept(side, layer);
        rotateWhenLayerIsEdgy(side, layer);
        switch (side) {
            case front -> rotateFront(layer);
            case back -> rotateBack(layer);
            case top -> rotateTop(layer);
            case bottom -> rotateBottom(layer);
            case left -> rotateLeft(layer);
            case right -> rotateRight(layer);
        }
        afterRotation.accept(side,layer);

        if (side == axis[my_axis][0]) layers[layer].release();
        else layers[size - layer - 1].release();

        //System.out.println("I am entering final protocole.");
        final_protocol();
       // System.out.println("I have rotated (" + side + ", " + layer + ")");
    }

    public String show() throws InterruptedException {
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
        return result.toString();
    }

    public static void main(String[] args) throws InterruptedException {
        var counter = new Object() { int value = 0; };
        Cube c = new Cube(3,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> ++counter.value,
                () -> ++counter.value
        );
        //int n = c.size;
        /*c.fields[front][0][0] = 1;
        c.fields[front][0][1] = 4;
        c.fields[front][0][2] = 4;
        c.fields[front][1][0] = 2;
        c.fields[front][1][1] = 5;
        c.fields[front][1][2] = 2;
        c.fields[front][2][0] = 3;
        c.fields[front][2][1] = 1;
        c.fields[front][2][2] = 1;*/

        Shower.setDefaultMode(Shower.GRID | Shower.COLOR);
        System.out.println(utils.Shower.show(c.show()));
        c.rotate(right, 0);
        c.rotate(left, 0);
        c.rotate(top, 0);
        c.rotate(front, 1);
        c.rotate(top, 0);
        c.rotate(back,2);
        c.rotate(bottom, 1);
        c.rotate(left,2); // chyba dotąd działa.
        System.out.println(utils.Shower.show(c.show()));
    }

}

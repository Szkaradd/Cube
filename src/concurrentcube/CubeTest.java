package concurrentcube;

import org.junit.jupiter.api.Test;
import utils.Shower;
import utils.Stopwatch;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;
public class CubeTest {
    private static final int top = 0;
    private static final int left = 1;
    private static final int front = 2;
    private static final int right = 3;
    private static final int back = 4;
    private static final int bottom = 5;
    private static final int NUMBER_OF_SIDES = 6;
    private static final int AMMOUNT_OF_THREADS = 8;
    private final Random random = new Random();

    private static class ShowerRunnable implements Runnable {
        private final Cube c;
        private String show_string;

        private ShowerRunnable(Cube c) {
            this.c = c;
        }

        public String getShow_string() {
            return show_string;
        }

        @Override
        public void run() {
            try {
                show_string = c.show();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ShowerCallable implements Callable<String> {
        private final Cube c;

        public ShowerCallable(Cube c) {
            this.c = c;
        }

        @Override
        public String call() throws Exception {
            return c.show();
        }
    }

    private static class Rotator implements Runnable {
        private final int side;
        private final int layer;
        private final Cube c;

        private Rotator(int side, int layer, Cube c) {
            this.side = side;
            this.layer = layer;
            this.c = c;
        }

        @Override
        public void run() {
            try {
                c.rotate(side, layer);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Test sprawdza, czy zostało zachowane bezpieczeństwo.
    // Wykonuje wiele losowych ruchów, na koniec sprawdza, czy
    //
    @Test
    public void SafetyTest1() throws InterruptedException {
        // Shower.setDefaultMode(Shower.COLOR | Shower.GRID);
        AtomicInteger counter = new AtomicInteger(0);

        int cube_size = 10;
        int threads_amount = 1000;
        int tries = 100;
        Cube c = new Cube(cube_size,
                (x, y) -> counter.getAndIncrement(),
                (x, y) -> counter.getAndIncrement(),
                () -> counter.getAndIncrement(),
                () -> counter.getAndIncrement()
        );

        for (int i = 0; i < tries; i++) {
            c.resetFields();
            Thread[] threads = new Thread[threads_amount];
            for (int j = 0; j < threads_amount; j++) {
                threads[j] = new Thread(new Rotator(top, random.nextInt(cube_size), c));
            }
            for (Thread t : threads) t.start();
            for (int k = 0; k < threads_amount; k++) threads[k].join();
            assert c.correctCountOfNumbers();
        }
        assert counter.get() == threads_amount * tries * 2;
    }

    @Test
    // Test zleca wykonanie tych samych obrotów współbieżnie, jak i sekwencyjnie.
    // Pokazuje, że współbieżne wykonanie w przypadku sleepa w before rotation jest szybsze.
    public void ConcurrentVsSequential1() throws InterruptedException {
        int cube_size = 5;
        int threads_amount = 1000;
        int sleep_time = 1;
        Cube concurrent = new Cube(cube_size,
                (x, y) -> {
                    try {
                        sleep(sleep_time);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                },
                (x,y) -> {},
                () -> {},
                () -> {});

       Cube sequential = new Cube(cube_size,
               (x, y) -> {
                    try {
                    sleep(sleep_time);
                    } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                },
               (x,y) -> {},
               () -> {},
               () -> {});

        utils.Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        for (int i = 0; i < threads_amount; i++) {
            sequential.rotate(i % NUMBER_OF_SIDES, i % cube_size);
        }
        Duration seqDuration = stopwatch.stop();
        System.out.println("It took sequential " + seqDuration);

        stopwatch.start();

        Thread[] threads = new Thread[threads_amount];
        for (int i = 0; i < threads_amount; i++) {
            threads[i] = new Thread(new Rotator(i % NUMBER_OF_SIDES, i % cube_size, concurrent));
        }

        for (int i = 0; i < threads_amount; i++) threads[i].start();
        for (int i = 0; i < threads_amount; i++) threads[i].join();

        Duration concurrentDuration = stopwatch.stop();

        System.out.println("It took concurrent " + concurrentDuration);
        assert concurrentDuration.compareTo(seqDuration) < 0;
    }

    // Test polega na obracaniu sekwencyjnie każdą warstwą patrząc od strony top
    // oraz obracaniu tych samych warstw współbieżnie. Wątki naraz wykonują operację sleep.
    @Test
    public void ConcurrentVsSequential2() throws InterruptedException {
        int cube_size = 10;
        int threads_amount = 1000;
        int sleep_time = 1;
        Cube concurrent = new Cube(cube_size,
                (x, y) -> {
                    try {
                        sleep(sleep_time);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                },
                (x,y) -> {},
                () -> {},
                () -> {});

        Cube sequential = new Cube(cube_size,
                (x, y) -> {
                    try {
                        sleep(sleep_time);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                },
                (x,y) -> {},
                () -> {},
                () -> {});

        utils.Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        for (int i = 0; i < threads_amount; i++) {
            sequential.rotate(top, i % cube_size);
        }
        Duration seqDuration = stopwatch.stop();
        System.out.println("It took sequential " + seqDuration);

        stopwatch.start();

        Thread[] threads = new Thread[threads_amount];
        for (int i = 0; i < threads_amount; i++) {
            threads[i] = new Thread(new Rotator(top, i % cube_size, concurrent));
        }

        for (int i = 0; i < threads_amount; i++) threads[i].start();
        for (int i = 0; i < threads_amount; i++) threads[i].join();

        Duration concurrentDuration = stopwatch.stop();

        System.out.println("It took concurrent " + concurrentDuration);
        assert concurrentDuration.compareTo(seqDuration) < 0;
    }

    // Test pokazujący, że mogą wyjść różne outputy,
    // jeśli wielowątkowo chcemy wykonać Show() i 2 różne Rotate(side, layer);
    @Test
    public void showingRotatingConcurrent() throws ExecutionException, InterruptedException {

        Shower.setDefaultMode(Shower.COLOR | Shower.GRID);
        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> {},
                (x,y) -> {},
                () -> {},
                () -> {}
        );
        ExecutorService RotatePool = Executors.newFixedThreadPool(AMMOUNT_OF_THREADS);
        String[] possible_results = new String[6];
        possible_results[0] = c.show(); // show + (0,0) + (1,0)
        c.rotate(top, 0);
        possible_results[1] = c.show(); // (0,0) + show
        c.rotate(left,0);
        possible_results[2] = c.show(); // (0,0) + (1,0) + show
        c.resetFields();
        c.rotate(left, 0);
        possible_results[3] = c.show(); // (1,0) + show
        c.rotate(top,0);
        possible_results[4] = c.show(); // (1,0) + (0,0) + show
        c.resetFields();
        possible_results[5] = c.show(); // show + (1,0) + (0,0)

        int[] counters = new int[6];

        try {
            for (int i = 0; i < 1000; i++) {
                c.resetFields();
                Future<String> state = RotatePool.submit(new ShowerCallable(c));
                RotatePool.submit(new Rotator(top, 0, c));
                RotatePool.submit(new Rotator(left, 0, c));
                String cube_state = state.get();
                for (int j = 0; j < 5; j++) {
                    if (Objects.equals(possible_results[j], cube_state)) {
                        counters[j]++;
                    }
                }
            }
            for (int i = 0; i < 5; i++) {
                assert counters[i] > 0;
            }
        } finally {
            RotatePool.shutdown();
        }
    }

    // Prosty test sprawdzający poprawność obrotu przedniej warstwy.
    @Test
    public void simpleRotateFront() throws InterruptedException {
        String expected =
                "000" +
                "000" +
                "111" +

                "115" +
                "115" +
                "115" +

                "222" +
                "222" +
                "222" +

                "033" +
                "033" +
                "033" +

                "444" +
                "444" +
                "444" +

                "333" +
                "555" +
                "555";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> ++counter.value,
                () -> ++counter.value
        );

        c.rotate(front, 0);
        assert(expected.equals(c.show()) && counter.value == 4);
    }

    // Prosty test sprawdzający poprawność obrotu tylnej warstwy.
    @Test
    public void simpleRotateBack() throws InterruptedException {

        String expected =
                "333" +
                "000" +
                "000" +

                "011" +
                "011" +
                "011" +

                "222" +
                "222" +
                "222" +

                "335" +
                "335" +
                "335" +

                "444" +
                "444" +
                "444" +

                "555" +
                "555" +
                "111";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> ++counter.value,
                () -> ++counter.value
        );

        c.rotate(back, 0);
        assert(expected.equals(c.show()) && counter.value == 4);
    }

    // Prosty test sprawdzający poprawność obrotu lewej warstwy.
    @Test
    public void simpleRotateLeft() throws InterruptedException {

        String expected =
                "400" +
                "400" +
                "400" +

                "111" +
                "111" +
                "111" +

                "022" +
                "022" +
                "022" +

                "333" +
                "333" +
                "333" +

                "445" +
                "445" +
                "445" +

                "255" +
                "255" +
                "255";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> --counter.value,
                () -> --counter.value
        );

        c.rotate(left, 0);
        assert(expected.equals(c.show()) && counter.value == 0);
    }

    // Prosty test sprawdzający poprawność obrotu prawej warstwy.
    @Test
    public void simpleRotateRight() throws InterruptedException {

        String expected =
                "002" +
                "002" +
                "002" +

                "111" +
                "111" +
                "111" +

                "225" +
                "225" +
                "225" +

                "333" +
                "333" +
                "333" +

                "044" +
                "044" +
                "044" +

                "554" +
                "554" +
                "554";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> --counter.value,
                () -> --counter.value
        );

        c.rotate(right, 0);
        assert(expected.equals(c.show()) && counter.value == 0);
    }

    // Prosty test sprawdzający poprawność obrotu górnej warstwy.
    @Test
    public void simpleRotateTop() throws InterruptedException {

        String expected =
                "000" +
                "000" +
                "000" +

                "222" +
                "111" +
                "111" +

                "333" +
                "222" +
                "222" +

                "444" +
                "333" +
                "333" +

                "111" +
                "444" +
                "444" +

                "555" +
                "555" +
                "555";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> counter.value *= 2,
                () -> counter.value *= 2,
                () -> counter.value *= 2
        );

        c.rotate(top, 0);
        assert(expected.equals(c.show()) && counter.value == 1 << 3);
    }

    // Prosty test sprawdzający poprawność obrotu dolnej warstwy.
    @Test
    public void simpleRotateBottom() throws InterruptedException {

        String expected =
                "000" +
                "000" +
                "000" +

                "111" +
                "111" +
                "444" +

                "222" +
                "222" +
                "111" +

                "333" +
                "333" +
                "222" +

                "444" +
                "444" +
                "333" +

                "555" +
                "555" +
                "555";

        var counter = new Object() { int value = 0; };

        int cube_size = 3;
        Cube c = new Cube(cube_size,
                (x, y) -> ++counter.value,
                (x, y) -> counter.value *= 2,
                () -> counter.value *= 2,
                () -> counter.value *= 2
        );
        c.rotate(bottom, 0);
        assert(expected.equals(c.show()) && counter.value == 1 << 3);
    }

    @Test
    public void simpleInterruptTest() throws InterruptedException {
        int cube_size = 5;
        int thread_count = 1000;
        Cube c = new Cube(cube_size,
                (x,y) -> {},
                (x,y) -> {},
                () -> {},
                () -> {});
        Thread[] threads = new Thread[thread_count];
        for (int i = 0; i < thread_count; i++) {
            threads[i] = new Thread(new Rotator(random.nextInt(NUMBER_OF_SIDES), random.nextInt(cube_size), c));
        }
        for (Thread t: threads) t.start();
        threads[random.nextInt(thread_count)].interrupt();
        for (Thread t: threads) t.join();
        assert c.correctCountOfNumbers();
    }

    @Test
    public void concurrentRotationsCorrectness() throws InterruptedException {
        int cube_size = 5;
        int thread_count = 3;
        int tries = 10000;
        String[] possible_results = new String[6]; // 3! możliwych wyników.
        Cube c = new Cube(cube_size,
                (x,y) -> {},
                (x,y) -> {},
                () -> {},
                () -> {});

        // Generujemy możliwe wyniki.
        c.rotate(0,0);
        c.rotate(1,0);
        c.rotate(2,0);
        possible_results[0] = c.show();
        c.resetFields();

        c.rotate(1,0);
        c.rotate(0,0);
        c.rotate(2,0);
        possible_results[1] = c.show();
        c.resetFields();

        c.rotate(0,0);
        c.rotate(2,0);
        c.rotate(1,0);
        possible_results[2] = c.show();
        c.resetFields();

        c.rotate(1,0);
        c.rotate(2,0);
        c.rotate(0,0);
        possible_results[3] = c.show();
        c.resetFields();

        c.rotate(2,0);
        c.rotate(1,0);
        c.rotate(0,0);
        possible_results[4] = c.show();
        c.resetFields();

        c.rotate(2,0);
        c.rotate(0,0);
        c.rotate(1,0);
        possible_results[5] = c.show();

        int[] counter = new int[6];
        Shower.setDefaultMode(Shower.COLOR | Shower.GRID);

        Thread[] threads = new Thread[thread_count];
        for (int try_number = 0; try_number < tries; try_number++) {
            c.resetFields();
            threads[0] = new Thread(new Rotator(0, 0, c));
            threads[1] = new Thread(new Rotator(1, 0, c));
            threads[2] = new Thread(new Rotator(2, 0, c));
            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join();
            String result = c.show();
            boolean correct = false;
            for (int i = 0; i < 6; i++) {
                if (Objects.equals(result, possible_results[i])) {
                    correct = true;
                }
            }
            assert correct;
        }

    }
}
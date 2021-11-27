package concurrentcube;

import org.junit.jupiter.api.Test;
import utils.Shower;
import utils.Stopwatch;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static java.lang.Thread.sleep;

public class CubeTest {

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

    @Test
    public void Safety() throws InterruptedException {
        Shower.setDefaultMode(Shower.COLOR | Shower.GRID);
        var counter = new Object() { int value = 0; };

        Cube c;

        for (int i = 0; i < 20; i++) {
            c = new Cube(7,
                    (x, y) -> ++counter.value,
                    (x, y) -> ++counter.value,
                    () -> ++counter.value,
                    () -> ++counter.value
            );
            Thread[] threads = new Thread[20];
            for (int j = 0; j < 20; j++) {
                threads[j] = new Thread(new Rotator(1, (j + 6) % 7, c));
            }
            for (Thread t : threads) t.start();
            for (int k = 0; k < 10; k++) threads[k].join();
          //  if (Objects.equals(c.show(), c3.show())) {
         //       ile++;
                //System.out.println("tak samo" + i);}
        }
       // System.out.println(ile);
        //c.count_numbers();
        //System.out.println(Shower.show(c.show()));
        //System.out.println(Shower.show(c2.show()));
    }

    @Test
    public void ConcurrentFast() throws InterruptedException {
        int cube_size = 20;
        int threads_ammount = 10000;
        int sleep_time = 1;
        Cube concurrent = new Cube(cube_size, (x, y) -> {
            try {
                sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, (x,y) -> {}, () -> {}, () -> {});

        Cube sequential = new Cube(cube_size, (x, y) -> {
            try {
                sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, (x,y) -> {}, () -> {}, () -> {});

        Thread[] threads = new Thread[threads_ammount];

        utils.Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        for (int i = 0; i < threads_ammount; i++) {
            sequential.rotate(0, i % cube_size);
        }
        Duration SyncDuration = stopwatch.stop();
        System.out.println("It took synchronized " + SyncDuration);

        stopwatch.start();
        for (int i = 0; i < threads_ammount; i++) {
            threads[i] = new Thread(new Rotator(0, i % cube_size, concurrent));
        }

        for (int i = 0; i < threads_ammount; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threads_ammount; i++) {
            threads[i].join();
        }
        Duration concurrentDuration = stopwatch.stop();
        System.out.println("It took concurrent " + concurrentDuration);
        assert (Objects.equals(concurrent.show(), sequential.show()));
    }
}

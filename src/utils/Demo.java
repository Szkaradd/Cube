/**
 * Ten plik nie powinien być dołączany do rozwiązania.
 */

package utils;

import java.util.function.Function;

import concurrentcube.Cube;

public class Demo {

    private static void showerDemo() {

        Function<Integer, String> cube = sz -> "0".repeat(sz * sz) + "1".repeat(sz * sz) + "2".repeat(sz * sz)
                + "3".repeat(sz * sz) + "4".repeat(sz * sz) + "5".repeat(sz * sz);

        System.out.println(Shower.show("000000000111111111222222222333333333444444444555555555", Shower.NONE));
        System.out.println(Shower.show("000000000111111111222222222333333333444444444555555555", Shower.COLOR));
        System.out.println(Shower.show("000000000111111111222222222333333333444444444555555555", Shower.SQUARES));

        System.out.print(Shower.show(cube.apply(7), Shower.GRID));

        System.out.print(Shower.show("000000000111111111222222222333333333444444444555555555", Shower.GRID | Shower.COLOR));

        System.out.print(
            Shower.connect(
                "got\n" + Shower.show("000011112222333344445555", Shower.GRID | Shower.COLOR),
                "expected\n" + Shower.show("000011112222333344445555", Shower.GRID | Shower.SQUARES),
                " | "
            )
        );

        Shower.setDefaultMode(Shower.GRID | Shower.SQUARES);

        System.out.print(Shower.show("012345"));
        System.out.print(Shower.show("000011112222333344445555"));

        Shower.setDefaultMode(Shower.NONE);

        System.out.println(Shower.show("000011112222333344445555"));
        System.out.println(Shower.show("000011112222333344445555", Shower.COLOR));

    }

    private static void moverDemo() throws InterruptedException {
        Function<Integer,Cube> cube = size -> new Cube(size, (x,y) -> {}, (x,y) -> {}, () -> {}, () -> {});

        System.out.println(Mover.move(cube.apply(3), "U' D F' B L R' U' D"));
        
        Mover.states(cube.apply(3), "U U U U").forEach(System.out::println);

        Shower.setDefaultMode(Shower.GRID | Shower.SQUARES);
        
        System.out.print(Shower.show(Mover.move(cube.apply(3), "D2 F2 U' B2 F2 L2 R2 D R' B F D' U L R D2 U2 F' U2")));
        System.out.print(Shower.show(Mover.move(cube.apply(4), "F L F U' R U F2 L2 U' L' B D' B' L2 U Fw Lw Fw Uw' Rw Uw Fw2 Lw2 Uw' Lw' Bw Dw' Bw' Lw2 Uw")));

    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Przykład przysznica:");
        showerDemo();
        System.out.println();
        System.out.println("Przykład wnioskodawcy:");
        moverDemo();
    }

}

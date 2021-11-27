/** 
 * Nie jestem autorem tego pliku, ani żadnych innych plików w tym pakiecie.
 * Ten plik (i żaden inny plik w tym pakiecie) nie powinien podlegać ocenie.
 * Źródło: https://github.com/greg19/mimuwcube
 */

package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Map.entry;

import concurrentcube.Cube;

public class Mover {

    private Mover() {};

    private static void rotate(Cube cube, int side, int layer) {
        try {
            cube.rotate(side, layer);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Map<String, Consumer<Cube>> moves = Map.ofEntries(entry("U", cube -> rotate(cube, 0, 0)),
            entry("u", cube -> rotate(cube, 0, 1)), entry("L", cube -> rotate(cube, 1, 0)),
            entry("l", cube -> rotate(cube, 1, 1)), entry("F", cube -> rotate(cube, 2, 0)),
            entry("f", cube -> rotate(cube, 2, 1)), entry("R", cube -> rotate(cube, 3, 0)),
            entry("r", cube -> rotate(cube, 3, 1)), entry("B", cube -> rotate(cube, 4, 0)),
            entry("b", cube -> rotate(cube, 4, 1)), entry("D", cube -> rotate(cube, 5, 0)),
            entry("d", cube -> rotate(cube, 5, 1)), entry("M", cube -> rotate(cube, 1, 1)),
            entry("S", cube -> rotate(cube, 2, 1)), entry("E", cube -> rotate(cube, 5, 1)));

    private static void motion(Cube cube, String move) {
        if (!moves.containsKey(move))
            throw new IllegalArgumentException("Invalid single motion '" + move + "'");
        moves.get(move).accept(cube);
    }

    private static void composedMotion(Cube cube, String move) throws InterruptedException {
        if (move.length() == 1) {
            motion(cube, move);
            return;
        }
        if (move.charAt(move.length() - 1) == '2') {
            composedMotion(cube, move.substring(0, move.length() - 1));
            composedMotion(cube, move.substring(0, move.length() - 1));
            return;
        }
        if (move.charAt(move.length() - 1) == '\'') {
            composedMotion(cube, move.substring(0, move.length() - 1));
            composedMotion(cube, move.substring(0, move.length() - 1));
            composedMotion(cube, move.substring(0, move.length() - 1));
            return;
        }
        if (move.length() == 2 && move.charAt(1) == 'w') {
            motion(cube, move.substring(0, 1));
            motion(cube, move.substring(0, 1).toLowerCase());
            return;
        }
    }

    public static String move(Cube cube, String sequence) throws InterruptedException {
        for (String move : sequence.split(" "))
            composedMotion(cube, move);
        return cube.show();
    }

    public static List<String> states(Cube cube, String sequence) throws InterruptedException {
        List<String> states = new ArrayList<>();
        states.add(cube.show());
        for (String move : sequence.split(" ")) {
            motion(cube, move);
            states.add(cube.show());
        }
        return states;
    }

}

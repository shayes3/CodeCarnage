/*
 * Copyright (c) 2017. Licensed under the Apache License 2.0.
 * For full copyright, licensing, and sourcing information,
 * please refer to the CodeCarnage GitHub repository's README.md file
 * (found on https://github.com/j3kstrum/CodeCarnage).
 */

package engine.core;

import common.BaseLogger;
import common.constants.GameStatus;
import engine.data.EngineData;
import gui.game.GameGUI;
import interpreter.Check;
import interpreter.ScriptCommand;
import interpreter.enumerations.Command;
import interpreter.enumerations.Data;
import interpreter.enumerations.Operator;
import org.mapeditor.core.Map;
import org.mapeditor.io.TMXMapReader;
import utilties.models.EntityMap;
import utilties.models.Game;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The main class for the main.java.engine.
 * This class will be initialized and will be responsible for game timing, acting as a game pipeline, etc.
 * <p>
 * Created by jacob.ekstrum on 9/11/17.
 */
public class Engine {

    // The Engine's Database.
    private final EngineData DATA;

    // The amount of time for each game tick.
    public static final long TICK_TIME = 250;
    private boolean _inCoreGame = false;

    private static final BaseLogger ENGINE_LOGGER = new BaseLogger("Engine");

    // Currently holds hardcoded CPU script
    private List<ScriptCommand> cpuCommands;

    public static boolean TEST_FLAG = false;

    public Map map;
    public Game game;
    public GameGUI gameGUI;

    private boolean _isPlayerTurn = true;

    /**
     * Initializes the Engine and performs the main ticking loop.
     */
    public Engine(GameGUI gameGUI) {
        generateCPUScript();
        this.gameGUI = gameGUI;
        DATA = new EngineData();
        try {
            this.DATA.setMap(loadGameMap());
        } catch (NullPointerException ne) {
            ENGINE_LOGGER.fatal("COULD NOT LOAD GAME MAP.");
            if (!TEST_FLAG) {
                System.exit(1);
            }
        }
        ENGINE_LOGGER.info("Engine initialized. Beginning tick loop...");
    }

    /**
     *
     * @return The current state of the game (inactive, running, won, lost, stalemate)
     */
    public GameStatus getGameState() {
        System.out.println(game.getState());
        return game.getState();
    }

    /**
     * Loads the Tiled static EntityMap from disk.
     *
     * @return The initial GameMap, initialized to hold the static Tiled EntityMap.
     */
    private Map loadGameMap() {
        TMXMapReader mapReader = new TMXMapReader();
        URL mapPath = getClass().getResource("/game-map.tmx");
        try {
            this.map = mapReader.readMap(mapPath.toString());
        } catch (Exception ex) {
            ENGINE_LOGGER.warning("Could not load game map. Attempting to use *nix filepaths.");
            ENGINE_LOGGER.warning(ex.getMessage());
            String path = "../resources/main/nix/game-map.tmx";
//            System.out.println(mapPath == null ? "null" : mapPath.toString());
            try {
                this.map = mapReader.readMap(path);
            } catch (Exception ex2) {
                ENGINE_LOGGER.warning("Still failed to load game map. Attempting backup *nix filepaths.");
                ENGINE_LOGGER.critical(ex2.getMessage());
                try {
                    mapPath = getClass().getResource("/nix/game-map.tmx");
                    this.map = mapReader.readMap(mapPath.toString());
                } catch (Exception ex3) {
                    ENGINE_LOGGER.fatal(ex3.getMessage());
                    System.exit(1);
                }
            }
        }

        EntityMap entityMap = new EntityMap(map, map.getWidth(), map.getHeight());
        game = new Game(entityMap);
        this.gameGUI._map = map;
        return map;
    }

    /**
     *
     * @return True if the engine is running, False otherwise.
     */
    public boolean isRunning() {
        return this._inCoreGame;
    }

    /**
     * Wrapper for shutdown(boolean now) that uses default argument now=false.
     *
     * @return The value returned by shutting down with now as false.
     */
    public boolean shutdown() {
        return this.shutdown(false);
    }

    /**
     * Safe shutdown handler for the engine that can be called from external functions.
     *
     * @param now Whether to immediately terminate the program at the end of the tick or to wait.
     * @return true if successful, false if shutdown failed.
     */
    public boolean shutdown(boolean now) {
        ENGINE_LOGGER.info("Shutting down...");
        if (now) {
            this._inCoreGame = false;
        } else {
            ENGINE_LOGGER.warning("Currently can not shutdown engine with delay. " +
                    "Terminating immediately following current tick.");
            this._inCoreGame = false;
        }
        return true;
    }

    /**
     * Ensures that the proper amount of time since the last tick has elapsed.
     * If not, sleeps until this condition is met.
     *
     * @param lastTick The system time polled at the last tick.
     * @return The system time at the end of the method, which is the start of the next tick.
     */
    public long performWait(long lastTick) {
        long delta = System.currentTimeMillis() - lastTick;
        if (delta > Engine.TICK_TIME) {
            ENGINE_LOGGER.critical("System too slow for tick. Elapsed delta: " + String.valueOf(delta) + ".");
        } else {
            try {
                if (delta < Engine.TICK_TIME / 2) {
                    ENGINE_LOGGER.info("Delta: " + delta + " (" + (100 * delta / Engine.TICK_TIME) + "% utilization)");
                } else {
                    ENGINE_LOGGER.warning(
                            "Delta: " + delta + " (" + (100 * delta / Engine.TICK_TIME) + "% utilization)"
                    );
                }
                Thread.sleep(Engine.TICK_TIME - delta);
            } catch (InterruptedException ie) {
                ENGINE_LOGGER.critical("Tick sleep interrupted!");
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * Performs all of the necessary game logic for one tick, or turn, if currently in-game.
     */
    public Map tick() {

        if (game == null) {
            ENGINE_LOGGER.critical("Game was null. Returning null.");
            return null;
        }

        //If this game is over, we just want to return the previous map and wait until overlay is displayed
        if(!game.isGameOver()) {

            if (_isPlayerTurn) {

                List<ScriptCommand> playerCommands = this.gameGUI.getCommandObjects();

                boolean playerCommandExecuted = false;
                for (ScriptCommand pc : playerCommands) {
                    boolean executed = pc.doCommand(this.game, 0);
                    if (executed) {
                        playerCommandExecuted = true;
                        break;
                    }
                }
                if (!playerCommandExecuted) this.game.doNothing(0);

            } else {

                boolean computerCommandExecuted = false;
                for (ScriptCommand cc : this.cpuCommands) {
                    boolean executed = cc.doCommand(this.game, 1);
                    if (executed) {
                        computerCommandExecuted = true;
                        break;
                    }
                }
                if (!computerCommandExecuted) this.game.doNothing(1);

            }
            this._isPlayerTurn = !_isPlayerTurn;
        }

        return game.nextTurn();
    }

    /**
     * Performs code cleanup.
     *
     * @return true if successful, false otherwise.
     */
    public boolean cleanup() {
        // TODO: Populate
        ENGINE_LOGGER.warning("Currently have no engine cleanup code.");
        return true;
    }

    /**
     * Starts the game from the engine's perspective.
     * NOTE: This is a *hard* game start; that is, the battle is occurring here.
     */
    public void startGame() {
        this._inCoreGame = true;
    }

    /**
     * Stops the core battle from the engine's perspective.
     */
    public void stopGame() {
        this._inCoreGame = false;
    }

    public void generateCPUScript(){

        this.cpuCommands = new ArrayList<>();
        
        if(getRandomBoolean()){
            ArrayList<Check> checks = new ArrayList<>();
            ArrayList<Check> checksForAttack = new ArrayList<>();

            checks.add(new Check(Data.DISTANCE_FROM_OPPONENT.text(), "1",  Operator.GREATER_THAN));
            ScriptCommand approach = new ScriptCommand(checks, Command.APPROACH);
            this.cpuCommands.add(approach);

            checksForAttack.add(new Check(Data.DISTANCE_FROM_OPPONENT.text(), Data.DISTANCE_FROM_OPPONENT.text(), Operator.EQUALS));
            ScriptCommand attack = new ScriptCommand(checksForAttack, Command.ATTACK);
            this.cpuCommands.add(attack);
        }
        else{

            ArrayList<Check> checksForApproach = new ArrayList<>();
            ArrayList<Check> checksForEvade = new ArrayList<>();
            ArrayList<Check> checksForAttack = new ArrayList<>();

            checksForApproach.add(new Check(Data.DISTANCE_FROM_OPPONENT.text(), "10",  Operator.GREATER_THAN));
            ScriptCommand approach = new ScriptCommand(checksForApproach, Command.APPROACH);
            this.cpuCommands.add(approach);
            checksForEvade.add(new Check(Data.DISTANCE_FROM_OPPONENT.text(),"5", Operator.GREATER_THAN));
            ScriptCommand evade = new ScriptCommand(checksForEvade, Command.EVADE);
            this.cpuCommands.add(evade);
            checksForAttack.add(new Check("1", "1", Operator.EQUALS));
            ScriptCommand attack = new ScriptCommand(checksForAttack, Command.ATTACK);
            this.cpuCommands.add(attack);
        }
    }

    //TODO Utilize Seed generated from engine
    public boolean getRandomBoolean() {
        return ThreadLocalRandom.current().nextInt(0, 2) == 1;
    }

}
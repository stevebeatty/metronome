package com.example.beatty.metronome;

import android.media.SoundPool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thread that plays sounds in a SoundPool in order to prevent lag on the main UI thread
 * Created by beatty on 3/1/2016.
 */
public class SoundThread extends Thread {

    /**
     * Represents the sound to play in a SoundPool
     */
    public static class Sound {
        private final int soundID;
        private final float volume;

        public Sound(int soundID, float volume) {
            this.soundID = soundID;
            this.volume = volume;
        }

        public int getSoundID() {
            return soundID;
        }

        public float getVolume() {
            return volume;
        }

    }

    private boolean running;
    private SoundPool soundPool;
    // provides synchronization with other threads
    private BlockingQueue<Sound> sounds = new LinkedBlockingQueue<Sound>();

    /**
     * Creates a thread that uses the soundPool to play sounds
     * @param soundPool
     */
    public SoundThread (SoundPool soundPool) {
        this.soundPool = soundPool;
    }

    /**
     * While the thread is set to run, sounds will be removed from the queue and played on the
     * soundPool
     */
    @Override
    public void run() {
        Sound sound;
        while (running) {
            try {
                sound = sounds.take();
                soundPool.play(sound.getSoundID(), sound.getVolume(), sound.getVolume(), 0, 0, 1f);
            } catch (InterruptedException e) {}

        }
    }

    /**
     * Add a sound to the queue of sounds to be played
     * @param sound
     */
    public void addSound(Sound sound) {
        sounds.add(sound);
    }

    /**
     * Whether the thread is currently running
     * @return
     */
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * Sets the state of the thread.  {@link #start()} must be called to actually
     * run the thread
     * @param running
     */
    public synchronized void setRunning(boolean running) {
        this.running = running;
    }

}

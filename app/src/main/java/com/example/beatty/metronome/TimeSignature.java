package com.example.beatty.metronome;

/**
 * Represents the time signature on a measure in music.
 * Created by beatty on 2/26/2016.
 */
public class TimeSignature {

    public static final TimeSignature COMMON_TIME = new TimeSignature(4, 4);

    private final int beatsPerMeasure;
    private final int beatType;

    public TimeSignature(int beatsPerMeasure, int beatType) {
        this.beatsPerMeasure = beatsPerMeasure;
        this.beatType = beatType;
    }

    public int getBeatType() {
        return beatType;
    }

    public int getBeatsPerMeasure() {
        return beatsPerMeasure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSignature)) return false;

        TimeSignature that = (TimeSignature) o;

        if (beatsPerMeasure != that.beatsPerMeasure) return false;
        return beatType == that.beatType;

    }

    @Override
    public int hashCode() {
        int result = beatsPerMeasure;
        result = 31 * result + beatType;
        return result;
    }

    @Override
    public String toString() {
        return beatsPerMeasure + " - " + beatType;
    }
}

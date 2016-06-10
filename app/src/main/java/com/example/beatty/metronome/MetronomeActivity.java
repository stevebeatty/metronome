package com.example.beatty.metronome;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Simple single screen metronome
 */
public class MetronomeActivity extends AppCompatActivity {

    /**
     * Default font size for notes
     */
    public static final float NOTE_TEXT_SIZE = 70f;

    /**
     * The amount of margin above the notes
     */
    public static final int NOTES_MARGIN_TOP = 40;

    /**
     * The available time signatures in the dropdown menu
     */
    public static final List<TimeSignature> TIME_SIGNATURES =
            Arrays.asList(
                    TimeSignature.COMMON_TIME,
                    new TimeSignature(3, 4),
                    new TimeSignature(2, 4),
                    new TimeSignature(6, 8),
                    new TimeSignature(3, 8),
                    new TimeSignature(9, 8)
            );

    /**
     * The current index of the note that is being played in the {@link #notes} list
     */
    private int noteIndex = -1;

    /**
     * A list of notes that will be played and displayed
     */
    private List<TextView> notes;

    /**
     * The width of the displayed notes
     */
    private float notesWidth = 0;

    /**
     * The position of the rightmost coordinate for the displayed ntoes
     */
    private float notesRight = 0;

    /**
     * The number of beats to play per minite
     */
    private int beatsPerMinute = 120;

    /**
     * The time signature to use when playing the beat
     */
    private TimeSignature timeSignature;

    /**
     * The number of subdivisions of notes to display from the main beat type.  Limited by
     * the type of beat note
     */
    private int beatSubdivision = 1;

    /**
     * A timer that is used to play the beat sounds and update the highlighted notes
     */
    private Handler noteTimer;

    /**
     * Pool used to play the metronome sounds
     */
    private SoundPool soundPool;

    /**
     * The sound to play for the first beat of a measure
     */
    private SoundThread.Sound emphasisSound;

    /**
     * The sound to play for beats that are not the first
     */
    private SoundThread.Sound tickSound;

    /**
     * Flag to indicate whether the metronome is currenly playing
     */
    private boolean metronomeOn = false;

    /**
     * The thread used to play sounds so that the UI does not lag
     */
    private SoundThread soundThread;

    /**
     * The color to use for beat notes
     */
    private int noteHighlightColor;

    /**
     * The color to use for subdivision notes
     */
    private int noteSubdivisionHighlightColor;

    /**
     * The color to use for notes that are not active
     */
    private int noteUnhighlightColor;

    /**
     * The animator for the spark indicator
     */
    private ObjectAnimator anim;

    /**
     * Array adapter for the subdivision spinner
     */
    private ArrayAdapter<CharSequence> subdivisionAdapter;

    /**
     * Flag to determine if a request to redraw the notes has already been issued, but not yet laid out
     */
    private boolean requestRedraw = false;

    /**
     * The Runnable that is executed on each tick of the {@link #noteTimer}
     */
    private Runnable noteRunner = new Runnable() {
        @Override
        public void run() {
            if (!metronomeOn) {
                return;
            }

            if (noteIndex != -1) {
                unhighlightNote(notes.get(noteIndex));
            }

            noteIndex++;

            if (noteIndex >= notes.size()) {
                noteIndex = 0;
            }

            if (noteIndex == 0) {
                anim.setCurrentPlayTime(0);
                anim.start();

                playSound(emphasisSound);
            }

            if ((noteIndex) % beatSubdivision == 0) {
                highlightNote(notes.get(noteIndex));
                if (noteIndex != 0) playSound(tickSound);
            } else {
                highlightSubdivisionNote(notes.get(noteIndex));
            }


            scheduleNextNote();
        }
    };

    /**
     * Initialize and start the sound thread
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metronome);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.main_layout);
        layout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                Log.d("layout", "layoutchanged");
                requestRedraw = false;
            }
        });

        // Colors
        noteHighlightColor = ContextCompat.getColor(getApplicationContext(), R.color.colorNoteHighlight);
        noteSubdivisionHighlightColor = ContextCompat.getColor(getApplicationContext(), R.color.colorNoteSubdivisionHighlight);
        noteUnhighlightColor = ContextCompat.getColor(getApplicationContext(), R.color.colorNoteUnhighlight);

        timeSignature = TimeSignature.COMMON_TIME;
        notes = new ArrayList<>();

        noteTimer = new Handler();

        setupTimeSignatureSpinner();
        setupSubdivisionSpinner();

        // handlers must be created first
        setupBPMSeekBar();

        createOldSoundPool();

        soundThread = new SoundThread(soundPool);
        soundThread.setRunning(true);
        soundThread.start();
    }

    /**
     * Loads sounds
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.d("resume", "resume");

        int soundId = soundPool.load(this, R.raw.kick, 1);
        tickSound = new SoundThread.Sound(soundId, 0.8f);
        emphasisSound = new SoundThread.Sound(soundId, 1.0f);
    }

    /**
     * Stops the metronome and unloads sounds
     */
    @Override
    public void onPause() {
        super.onPause();
        Log.d("pause", "pause");

        stopMetronome();

        soundPool.unload(tickSound.getSoundID());
        soundPool.unload(emphasisSound.getSoundID());
    }

    /**
     * Stops the sound thread
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        soundThread.setRunning(false);

        Log.d("destroy", "destroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_metronome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return super.onOptionsItemSelected(item);
    }

    /**
     * Setup the beats per minute seekbar
     */
    private void setupBPMSeekBar() {
        final TextView textView = (TextView)findViewById(R.id.seekDisplay);
        SeekBar seekBar = (SeekBar)findViewById(R.id.seek1);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textView.setText("" + progress);
                beatsPerMinute = progress;
                setSparkSpeed();

                if (metronomeOn) {
                    stopMetronome();
                    startMetronome();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekBar.setProgress(beatsPerMinute);
    }

    /**
     * Sets up the time signature spinner
     */
    private void setupTimeSignatureSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.timeSignatures);
        ArrayAdapter<TimeSignature> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TIME_SIGNATURES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d("sig", "position: " + position + " signature: " + TIME_SIGNATURES.get(position));
                timeSignature = TIME_SIGNATURES.get(position);
                setSubdivisionOptions();
                redrawNotes();

                if (metronomeOn) {
                    stopMetronome();
                    startMetronome();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * Sets up the subdivision spinner
     */
    private void setupSubdivisionSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.subdivisionSpinner);
        subdivisionAdapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
        subdivisionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(subdivisionAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d("subd", "position: " + position + " subdivision: " + subdivisionAdapter.getItem(position));
                setSubdivision(position);
                redrawNotes();

                if (metronomeOn) {
                    stopMetronome();
                    startMetronome();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setSubdivisionOptions();
    }

    /**
     * Sets the options in the subdivision spinner based on the current time signature
     */
    private void setSubdivisionOptions() {
        subdivisionAdapter.clear();

        if (timeSignature.getBeatType() <= 16) subdivisionAdapter.add("1");
        if (timeSignature.getBeatType() <= 8) subdivisionAdapter.add("2");
        if (timeSignature.getBeatType() <= 4) subdivisionAdapter.add("4");

        Spinner spinner = (Spinner) findViewById(R.id.subdivisionSpinner);

        // find the last selected index
        int pos = spinner.getSelectedItemPosition();
        if (pos >= subdivisionAdapter.getCount()) {
            pos = subdivisionAdapter.getCount() - 1;
            spinner.setSelection(pos);
            setSubdivision(pos);
        }

        Object item = spinner.getSelectedItem();
        Log.d("subd", "item: " + item);
    }

    /**
     * Sets the subdivision value based on the spinner setting
     * @param position
     */
    private void setSubdivision(int position) {
        beatSubdivision = Integer.parseInt(subdivisionAdapter.getItem(position).toString());
        Log.d("subd", "sub position is: " + position + " subdiv is: " + beatSubdivision);
    }

    /**
     * The delay that is used to get the correct beats per minute
     * @return
     */
    private long soundDelayMilliSec() {
        return (long)((1000f * 60) / beatsPerMinute);
    }

    /**
     * Graphics may update more frequently than sound depending on the beatType and beatsPerMeasure
     * @return
     */
    private long graphicDelayMilliSec() {
        float freq = 1f/beatSubdivision;
        return (long)(soundDelayMilliSec() * freq);
    }

    /**
     * Schedules the next callback to the {@link #noteRunner}
     * @param immediate true if no delay or uses {@link #graphicDelayMilliSec()}
     */
    private void scheduleNextNote(boolean immediate) {
        long delay = immediate ? 0 : graphicDelayMilliSec();

        noteTimer.removeCallbacks(noteRunner);
        noteTimer.postDelayed(noteRunner, delay);
    }

    /**
     * Schedules a non-immediate next note
     */
    private void scheduleNextNote() {
        scheduleNextNote(false);
    }

    /**
     * Starts the metronome and resets the note played to the beginning
     */
    private void startMetronome() {
        noteIndex = -1;

        scheduleNextNote(true);

        startSparkAnimation();

        metronomeOn = true;
    }

    /**
     * Stops the metronome and disables animation
     */
    private void stopMetronome() {
        noteTimer.removeCallbacks(noteRunner);

        stopSparkAnimation();

        unhighlightAllNotes();

        metronomeOn = false;
    }

    /**
     * Starts and displays the spark at the beginning
     */
    private void startSparkAnimation() {
        anim.setCurrentPlayTime(0);
        anim.start();

        findViewById(R.id.spark).setVisibility(View.VISIBLE);
    }

    /**
     * Stops and hides the spark
     */
    private void stopSparkAnimation() {
        if (anim != null) anim.cancel();

        findViewById(R.id.spark).setVisibility(View.INVISIBLE);
    }

    /**
     * Creates the notes if a request to do so is not already pending
     */
    private void redrawNotes() {
        if (!requestRedraw) {
            requestRedraw = true;
            createNotes();
        }
    }

    /**
     * Gets the note string to use based on type and subdivision
     * @param type the type of beat
     * @param subdivisions the number of subdivisions of that beat
     * @return
     */
    private String getNoteString(int type, int subdivisions) {
        switch (type) {
            case 4:
                switch (subdivisions) {
                    case 4:
                        return getResources().getString(R.string.sixteenth_note);
                    case 2:
                        return getResources().getString(R.string.eigth_note);
                }
                return getResources().getString(R.string.quarter_note);
            case 8:
                if (subdivisions == 2) return getResources().getString(R.string.sixteenth_note);
                return getResources().getString(R.string.eigth_note);
            case 16:
                return getResources().getString(R.string.sixteenth_note);
        }

        return "";
    }

    /**
     * Creates the TextViews used to display notes and initializes the animations with the correct speed
     */
    private void createNotes() {
        // style for the notes
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(NOTE_TEXT_SIZE);
        paint.setColor(noteUnhighlightColor);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.main_layout);
        int width = layout.getWidth();

        Typeface font = Typeface.createFromAsset(getAssets(), "fonts/FreeSerif.otf");
        paint.setTypeface(font);

        // for the beat type find the width
        String note = getNoteString(timeSignature.getBeatType(), beatSubdivision);

        // the width of one note
        float singleWidth = paint.measureText(note) * metrics.density;

        int notesCount = timeSignature.getBeatsPerMeasure() * beatSubdivision;
        notesWidth = notesCount * singleWidth;

        // if the default text is too big then resize
        float maxWidth = width - layout.getPaddingLeft() - layout.getPaddingRight();
        if (notesWidth > maxWidth) {
            float ratio = notesWidth/maxWidth;
            paint.setTextSize(NOTE_TEXT_SIZE/ratio);
            singleWidth = paint.measureText(note) * metrics.density;
            notesWidth = notesCount * singleWidth;
        }

        // this is the left boundary of the notes, but the actual draw location will be moved by padding
        int sideWidth = (int) (width - notesWidth) / 2;

        notesRight = sideWidth + notesWidth;

        Log.d("size", "side width is " + sideWidth + ", noteswidth is " + notesWidth + " singleWidth is " + singleWidth + " padding: " + layout.getPaddingLeft() + " layout width " + width + " metrics width " + metrics.widthPixels);

        View parent = findViewById(R.id.toggle_button);

        Log.d("notes", "notes size " + notes.size() + ", beats " + timeSignature.getBeatsPerMeasure());

        // remove extra notes, if any
        for (int i = notes.size() - 1; i >= notesCount; i--) {
            TextView view = notes.get(i);
            layout.removeView(view);
            notes.remove(i);
        }

        // add notes, if needed
        for (int i = notes.size(); i < notesCount; i++) {
            TextView view = new TextView(this);
            layout.addView(view);
            notes.add(view);
        }

        createSparkAnimator();
        setSparkSpeed();

        for (int i = 0; i < notesCount; i++) {
            TextView view = notes.get(i);
            initializeNote(view, parent, (int) (sideWidth + i * singleWidth) - layout.getPaddingLeft(), paint, note);
        }
    }

    /**
     * Creates the animator for the spark based on the calculations in {@link #createNotes()}
     */
    private void createSparkAnimator() {
        TextView spark = (TextView)findViewById(R.id.spark);
        float size = spark.getPaint().measureText(spark.getText(), 0, 1);

        if (anim != null) anim.cancel();

        anim = ObjectAnimator.ofFloat(spark, "x", notesRight - notesWidth - size/2, notesRight - size/2);
        anim.setInterpolator(new LinearInterpolator());
    }

    /**
     * Sets the duration of the animation based on the size taken up the notes and the current beats per minute
     */
    private void setSparkSpeed() {
        float delay = timeSignature.getBeatsPerMeasure() * 60f / beatsPerMinute; // time between notes
        float speed = notesWidth/delay;

        if (anim != null) anim.setDuration((long) (delay * 1000));

        Log.d("speed", "speed " + speed + " delay: " + delay);
    }

    /**
     * Initializes a TextView with a note string and style and positions it on the screen
     * @param view TextView that will display the note
     * @param parent The reference view that the notes appear below
     * @param margin The left margin for the view
     * @param paint The style to use for the view
     * @param string The note string
     * @return
     */
    private TextView initializeNote(TextView view, View parent, int margin, Paint paint, String string) {
        view.setText(string);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.BELOW, parent.getId());
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        lp.setMargins(margin, NOTES_MARGIN_TOP, 0, 0);
        view.setTextColor(paint.getColor());
        view.setTextSize(paint.getTextSize());
        view.setTypeface(paint.getTypeface());

        view.setLayoutParams(lp);

        return view;
    }

    /**
     * New style of sound pool creation
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createNewSoundPool() {
        AudioAttributes att = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(att)
                .build();
    }

    /**
     * Old style of sound pool creation
     */
    private void createOldSoundPool() {
        soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {

            }
        });
    }

    /**
     * Adds a sound to the queue used by the sound thread
     * @param sound
     */
    public void playSound(SoundThread.Sound sound) {
        soundThread.addSound(sound);
    }

    /**
     * Toggles the state of the metronome and sets the button text
     * @param view
     */
    public void toggleActive(View view) {
        Button toggleButton = (Button)findViewById(R.id.toggle_button);
        if (!metronomeOn) {
            startMetronome();
            toggleButton.setText(getResources().getString(R.string.button_stop));
        } else {
            stopMetronome();
            toggleButton.setText(getResources().getString(R.string.button_start));
        }
    }

    /**
     * Highlights a main beat note
     * @param view
     */
    private void highlightNote(TextView view) {
        view.setTextColor(noteHighlightColor);
    }

    /**
     * Highlights a subdivision (non-main beat note)
     * @param view
     */
    private void highlightSubdivisionNote(TextView view) {
        view.setTextColor(noteSubdivisionHighlightColor);
    }

    /**
     * Removes highlighting from a note
     * @param view
     */
    private void unhighlightNote(TextView view) {
        view.setTextColor(noteUnhighlightColor);
    }

    /**
     * Remove highlighting from all notes
     */
    private void unhighlightAllNotes() {
        for (TextView view : notes) {
            unhighlightNote(view);
        }
    }

}

package voicesplitting.gui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import voicesplitting.parsing.EventParser;
import voicesplitting.time.TimeTracker;
import voicesplitting.trackers.NoteListGenerator;
import voicesplitting.utils.MidiNote;

/**
 * A <code>BeatTrackingRunner</code> is the class which interfaces between a
 * {@link VoiceSplittingGUI} and the actual program logic.
 * 
 * @author Andrew McLeod - 17 June, 2015
 */
public class VoiceSplittingRunner {

	/**
	 * The currently loaded MIDI file.
	 */
	private File midiFile;
	
	/**
	 * The TimeTracker for the currently loaded file.
	 */
	private TimeTracker tt;
	
	/**
	 * A List of the notes of the currently loaded file.
	 */
	private List<MidiNote> notes;
	
	/**
	 * A List of the gold standard voices for this song.
	 */
	private List<List<MidiNote>> goldStandardVoices;
	
	/**
	 * Create a new BeatTrackingRunner on the given File.
	 * 
	 * @param midiFile {@link #midiFile}
	 * @throws IOException If some I/O error occurred when reading the file.
	 * @throws InvalidMidiDataException If the file contained some invlaid MIDI data.
	 * @throws InterruptedException If this is running on a GUI and gets cancelled.
	 */
	public VoiceSplittingRunner(File midiFile) throws InvalidMidiDataException, IOException, InterruptedException {
		this.midiFile = midiFile;
		tt = new TimeTracker();
		NoteListGenerator nlg = new NoteListGenerator(tt);
		EventParser ep = new EventParser(midiFile, nlg, tt);
		
		ep.run();
		goldStandardVoices = ep.getGoldStandardVoices();
		
		notes = nlg.getNoteList();
	}
	
	/**
	 * Get a list of the gold standard voices for this song.
	 * 
	 * @return {@link #goldStandardVoices}
	 */
	public List<List<MidiNote>> getGoldStandardVoices() {
		return goldStandardVoices;
	}
	
	/**
	 * Get the TimeTracker used in this runner.
	 * 
	 * @return {@link #tt}
	 */
	public TimeTracker getTimeTracker() {
		return tt;
	}
	
	/**
	 * Get the List of the notes from the current loaded MIDI file.
	 * 
	 * @return {@link #notes}, or null if no MIDI file is loaded yet.
	 */
	public List<MidiNote> getNotes() {
		return notes;
	}
	
	/**
	 * Get the MIDI file associated with this runner.
	 * 
	 * @return {@link #midiFile}
	 */
	public File getFile() {
		return midiFile;
	}
}
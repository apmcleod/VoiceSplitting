package voicesplitting.voice.hmm;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import voicesplitting.utils.MathUtils;
import voicesplitting.utils.MidiNote;
import voicesplitting.voice.Voice;
import voicesplitting.voice.VoiceSplittingModelState;

/**
 * An <code>HmmVoiceSplittingModelState</code> is used to store an {@link HmmVoiceSplittingModel}'s
 * current state. It contains a List of the {@link Voice}s which are currently present in the
 * model, as well as its cumulative probability so far.
 *
 * @author Andrew McLeod - 7 April, 2015
 * @version 1.0
 * @since 1.0
 */
public class HmmVoiceSplittingModelState extends VoiceSplittingModelState implements Comparable<HmmVoiceSplittingModelState> {

	/**
	 * A List of the Voices present in this state.
	 */
	private List<Voice> voices;

	/**
	 * The log of the probability of this State occurring.
	 */
	private double logProb;

	/**
	 * The parameters we are using.
	 */
	private HmmVoiceSplittingModelParameters params;

	/**
	 * Create a new default State with logProb = 0 (ie. prob = 1)
	 *
	 * @param params {@link #params}
	 */
	public HmmVoiceSplittingModelState(HmmVoiceSplittingModelParameters params) {
		this(0, params);
	}

	/**
	 * Create a new empty State with the given log probability and parameters.
	 *
	 * @param logProb {@link #logProb}
	 * @param params {@link #params}
	 */
	public HmmVoiceSplittingModelState(double logProb, HmmVoiceSplittingModelParameters params) {
		this(logProb, new ArrayList<Voice>(), params);
	}

	/**
	 * Create a new State with the given log probability, voices, and parameters.
	 *
	 * @param logProb {@link #logProb}
	 * @param voices {@link #voices}
	 * @param params {@link #params}
	 */
	private HmmVoiceSplittingModelState(double logProb, List<Voice> voices, HmmVoiceSplittingModelParameters params) {
		this.voices = voices;
		this.logProb = logProb;
		this.params = params;
	}

	/**
	 * Return a TreeSet of all of the possible HmmVoiceSplittingModelStates which we could tansition into
	 * given the List of MidiNotes. This is done here using the
	 * {@link #getAllCandidateNewStatesRecursive(List, List, List, double, int)} method.
	 * <p>
	 * NOTE: It is assumed that the notes Lists passed into this method will be passed
	 * chronologically. Specifically, each time this method is invoked, it should be passed
	 * the List of MidiNotes which occur next in the MIDI song currently being read.
	 * <p>
	 * Usually, this method is simply called by some {@link HmmVoiceSplittingModel}'s
	 * {@link HmmVoiceSplittingModel#handleIncoming(List)} method.
	 *
	 * @param notes A List of the MidiNotes on which we need to transition.
	 * @return A TreeSet of HmmVoiceSplittingModelStates which we've transitioned into. The new HmmVoiceSplittingModelStates
	 * should not hold any common mutable objects, since they may modify them in the future.
	 */
	@Override
	public TreeSet<HmmVoiceSplittingModelState> handleIncoming(List<MidiNote> notes) {
		// We need a deep copy of voices in case there are more transitions to perform
		// on this State
		return getAllCandidateNewStatesRecursive(getOpenVoiceIndices(notes, voices), notes, voices, logProb, 0);
	}

	/**
	 * This method does the work of getting all of the possible HmmVoiceSplittingModelStates which
	 * we could transition into for {@link #handleIncoming(List)} recursively.
	 * <p>
	 * It uses the helper methods {@link #addNewVoicesRecursive(List, List, List, double, int, double[], double, TreeSet)}
	 * to add {@link MidiNote}s into new {@link Voice}s, and
	 * {@link #addToExistingVoicesRecursive(List, List, List, double, int, double[], TreeSet)} to add
	 * {@link MidiNote}s into existing {@link Voice}s.
	 *
	 * @param openVoiceIndices The open voice indices for each note, generated by {@link #getOpenVoiceIndices(List, List)}
	 * initially.
	 * @param incoming A List of the incoming {@link MidiNote}s.
	 * @param newVoices A List of the {@link Voice}s in this HmmVoiceSplittingModelState as it is now. For each recursive call,
	 * this should be a deep copy since the {@link Voice}s that lie within will be changed.
	 * @param logProbSum The sum of the current log probability of this state, including any transitions
	 * already made recursively.
	 * @param noteIndex The index of the note which we are tasked with transitioning on.
	 *
	 * @return A List of all states which could be transitioned into given the parameters.
	 */
	private TreeSet<HmmVoiceSplittingModelState> getAllCandidateNewStatesRecursive(List<List<Integer>> openVoiceIndices, List<MidiNote> incoming,
			List<Voice> newVoices, double logProbSum, int noteIndex) {
		if (noteIndex == incoming.size()) {
			// Base case - no notes left to transition. Return a State based on the given Voices and log prob.
			TreeSet<HmmVoiceSplittingModelState> newStates = new TreeSet<HmmVoiceSplittingModelState>();
			newStates.add(new HmmVoiceSplittingModelState(logProbSum, new ArrayList<Voice>(newVoices), params));
			return newStates;
		}

		// Setup
		TreeSet<HmmVoiceSplittingModelState> newStates = new TreeSet<HmmVoiceSplittingModelState>();


		// Calculate transition probabilities for starting new voices
		if (HmmVoiceSplittingModelTester.MAX_VOICES > 0 && voices.size() < HmmVoiceSplittingModelTester.MAX_VOICES) {
			double[] newVoiceProbs = new double[newVoices.size() + 1];
			for (int i = 0; i < newVoiceProbs.length; i++) {
				newVoiceProbs[i] = getTransitionProb(incoming.get(noteIndex), -i - 1, newVoices);
			}

			int maxIndex = MathUtils.getMaxIndex(newVoiceProbs);

			if (maxIndex != -1) {
				// There is a good place to add a new voice
				addNewVoicesRecursive(openVoiceIndices, incoming, newVoices, logProbSum, noteIndex, newVoiceProbs, newVoiceProbs[maxIndex], newStates);
			}
		}

		// Add to existing voices
		double[] existingVoiceProbs = new double[openVoiceIndices.get(noteIndex).size()];
		for (int i = 0; i < existingVoiceProbs.length; i++) {
			existingVoiceProbs[i] = getTransitionProb(incoming.get(noteIndex), openVoiceIndices.get(noteIndex).get(i), newVoices);
		}

		addToExistingVoicesRecursive(openVoiceIndices, incoming, newVoices, logProbSum, noteIndex, existingVoiceProbs, newStates);

		return newStates;
	}

	/**
	 * This method does the work for {@link #getAllCandidateNewStatesRecursive(List, List, List, double, int)}
	 * of adding a {@link MidiNote} into a newly created {@link Voice}.
	 * <p>
	 * {@link #addToExistingVoicesRecursive(List, List, List, double, int, double[], TreeSet)} is used
	 * to add a {@link MidiNote} into an existing {@link Voice}.
	 *
	 * @param openVoiceIndices The open voice indices for each note, generated by {@link #getOpenVoiceIndices(List, List)}
	 * initially.
	 * @param incoming A List of the incoming {@link MidiNote}s.
	 * @param newVoices A List of the {@link Voice}s in this HmmVoiceSplittingModelState as it is now. For each recursive call,
	 * this should be a deep copy since the {@link Voice}s that lie within will be changed.
	 * @param logProbSum The sum of the current log probability of this state, including any transitions
	 * already made recursively.
	 * @param noteIndex The index of the note which we are tasked with transitioning on.
	 * @param newVoiceProbs The probability of adding the current {@link MidiNote} into a new {@link Voice} at each possible index.
	 * @param maxValue The maximum value of any number in newVoiceProbs.
	 * @param newStates The List where we will add the newly created HmmVoiceSplittingModelStates.
	 */
	private void addNewVoicesRecursive(List<List<Integer>> openVoiceIndices, List<MidiNote> incoming, List<Voice> newVoices,
			double logProbSum, int noteIndex, double[] newVoiceProbs, double maxValue, TreeSet<HmmVoiceSplittingModelState> newStates) {

		if (HmmVoiceSplittingModelTester.MAX_VOICES > 0 && newVoices.size() < HmmVoiceSplittingModelTester.MAX_VOICES) {
			for (int newVoiceIndex = 0; newVoiceIndex < newVoiceProbs.length; newVoiceIndex++) {
				if (newVoiceProbs[newVoiceIndex] == maxValue) {
					// Add at any location with max probability
					doTransition(incoming.get(noteIndex), -newVoiceIndex - 1, newVoices);

					// Fix openVoiceIndices
					for (int note = noteIndex + 1; note < openVoiceIndices.size(); note++) {
						for (int voice = 0; voice < openVoiceIndices.get(note).size(); voice++) {
							if (openVoiceIndices.get(note).get(voice) >= newVoiceIndex) {
								openVoiceIndices.get(note).set(voice, openVoiceIndices.get(note).get(voice) + 1);
							}
						}
					}

					// (Pseudo-)recursive call
					newStates.addAll(getAllCandidateNewStatesRecursive(openVoiceIndices, incoming, newVoices, logProbSum + newVoiceProbs[newVoiceIndex], noteIndex + 1));

					// Fix for memory overflow - trim newStates as soon as we can
					while (newStates.size() > params.BEAM_SIZE) {
						newStates.pollLast();
					}

					// The objects are mutable, so reverse changes. This helps with memory usage as well.
					reverseTransition(-newVoiceIndex - 1, newVoices);

					// Reverse openVoiceIndices
					for (int note = noteIndex + 1; note < openVoiceIndices.size(); note++) {
						for (int voice = 0; voice < openVoiceIndices.get(note).size(); voice++) {
							if (openVoiceIndices.get(note).get(voice) > newVoiceIndex) {
								openVoiceIndices.get(note).set(voice, openVoiceIndices.get(note).get(voice) - 1);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * This method does the work for {@link #getAllCandidateNewStatesRecursive(List, List, List, double, int)}
	 * of adding a {@link MidiNote} into an existing {@link Voice}.
	 * <p>
	 * {@link #addNewVoicesRecursive(List, List, List, double, int, double[], double, TreeSet)} is used
	 * to add a {@link MidiNote} into a new {@link Voice}.
	 *
	 * @param openVoiceIndices The open voice indices for each note, generated by {@link #getOpenVoiceIndices(List, List)}
	 * initially.
	 * @param incoming A List of the incoming {@link MidiNote}s.
	 * @param newVoices A List of the {@link Voice}s in this HmmVoiceSplittingModelState as it is now. For each recursive call,
	 * this should be a deep copy since the {@link Voice}s that lie within will be changed.
	 * @param logProbSum The sum of the current log probability of this state, including any transitions
	 * already made recursively.
	 * @param noteIndex The index of the note which we are tasked with transitioning on.
	 * @param existingVoiceProbs The probability of adding the current {@link MidiNote} into a new {@link Voice} at each
	 * possible index.
	 * @param newStates The List where we will add the newly created HmmVoiceSplittingModelStates.
	 */
	private void addToExistingVoicesRecursive(List<List<Integer>> openVoiceIndices, List<MidiNote> incoming,
			List<Voice> newVoices, double logProbSum, int noteIndex, double[] existingVoiceProbs, TreeSet<HmmVoiceSplittingModelState> newStates) {

		for (int openVoiceIndex = 0; openVoiceIndex < existingVoiceProbs.length; openVoiceIndex++) {
			// Try the transition
			int voiceIndex = openVoiceIndices.get(noteIndex).get(openVoiceIndex);
			doTransition(incoming.get(noteIndex), voiceIndex, newVoices);

			// Fix openVoiceIndices
			boolean[] removed = new boolean[openVoiceIndices.size()];
			for (int note = noteIndex + 1; note < openVoiceIndices.size(); note++) {
				removed[note] = openVoiceIndices.get(note).remove(Integer.valueOf(voiceIndex));
			}

			// (Pseudo-)recursive call
			newStates.addAll(getAllCandidateNewStatesRecursive(openVoiceIndices, incoming, newVoices, logProbSum + existingVoiceProbs[openVoiceIndex], noteIndex + 1));

			// Remove extras from newStates to save memory
			while (newStates.size() > params.BEAM_SIZE) {
				newStates.pollLast();
			}

			// Reverse transition
			reverseTransition(voiceIndex, newVoices);

			// Reverse openVoiceIndices
			for (int j = noteIndex + 1; j < removed.length; j++) {
				if (removed[j]) {
					int note;
					for (note = 0; note < openVoiceIndices.get(j).size() && openVoiceIndices.get(j).get(note) < voiceIndex; note++);
					openVoiceIndices.get(j).add(note, voiceIndex);
				}
			}
		}
	}

	/**
	 * Get a List of the indices at which an existing {@link Voice} lies in the given List which
	 * each of thie given incoming {@link MidiNote}s could be added.
	 *
	 * @param incoming A List of the {@link MidiNote}s to check for open {@link Voice}s.
	 * @param voices A List of the {@link Voice}s we want to check.
	 *
	 * @return A List of the open voices in newVoices for each incoming note. <code>return.get(i).get(j)</code>
	 * will return the index of the (j+1)th (since it is 0-indexed) open {@link Voice} in newVoices for the ith
	 * {@link MidiNote} from incoming.
	 */
	private List<List<Integer>> getOpenVoiceIndices(List<MidiNote> incoming, List<Voice> voices) {
		long onsetTime = incoming.get(0).getOnsetTime();
		List<List<Integer>> openIndices = new ArrayList<List<Integer>>(incoming.size());

		for (MidiNote note : incoming) {
			List<Integer> noteOpen = new ArrayList<Integer>();
			for (int i = 0; i < voices.size(); i++) {
				if (voices.get(i).canAddNoteAtTime(onsetTime, note.getDurationTime(), params)) {
					noteOpen.add(i);
				}
			}
			openIndices.add(noteOpen);
		}

		return openIndices;
	}

	/**
	 * Reverse (undo) the given transition.
	 * <p>
	 * This is used when unwinding the recursive actions of
	 * {@link #getAllCandidateNewStatesRecursive(List, List, List, double, int)}.
	 *
	 * @param transition The value of the transition we want to perform on the given note.
	 * A negative value tells us that a {@link MidiNote} was added to a new {@link Voice} placed at index
	 * (-transition - 1). Any non-negative value tells us that a {@link MidiNote} was added into the existing
	 * {@link Voice} at that index in newVoices.
	 * @param newVoices The {@link Voice}s List which contains the transition we want to reverse.
	 */
	private void reverseTransition(int transition, List<Voice> newVoices) {
		// For new Voices, we need to add the Voice, and then update the transition value to
		// point to that new Voice so the lower code works.
		if (transition < 0) {
			newVoices.remove(-transition - 1);

		} else {
			newVoices.set(transition, newVoices.get(transition).getPrevious());
		}
	}

	/**
	 * Perform the given transition WITHOUT calculating probability.
	 * <p>
	 * The {@link #getTransitionProb(MidiNote, int, List)} method is used to calculate
	 * a transition's probability, and it should be called before calling this method.
	 *
	 * @param note The {@link MidiNote} we want to add to a {@link Voice}.
	 * @param transition The value of the transition we want to perform on the given {@link MidiNote}.
	 * A negative value tells us to add the {@link MidiNote} to a new {@link Voice} at index (-transition - 1).
	 * Any non-negative value tells us to add the {@link MidiNote} into the existing {@link Voice} at that
	 * index in newVoices.
	 * @param newVoices A List of the {@link Voice}s available to have the given {@link MidiNote} added to them.
	 */
	private void doTransition(MidiNote note, int transition, List<Voice> newVoices) {
		// For new Voices, we need to add the Voice, and then update the transition value to
		// point to that new Voice so the lower code works.
		if (transition < 0) {
			transition = -transition - 1;
			newVoices.add(transition, new Voice(note));

		} else {
			newVoices.set(transition, new Voice(note, newVoices.get(transition)));
		}
	}

	/**
	 * Get the probability of the given transition, but do not perform that transition.
	 * <p>
	 * This must be called before the given transition is actually performed with
	 * {@link #doTransition(MidiNote, int, List)}.
	 *
	 * @param note The {@link MidiNote} whose transition probability we want.
	 * @param transition The value of the transition whose probability we want to get, given
	 * the {@link MidiNote} we want to check. A negative value tells us to add the {@link MidiNote}
	 * to a new {@link Voice} at index (-transition - 1). Any non-negative value tells us to add the
	 * {@link MidiNote} into the existing {@link Voice} at that index in newVoices.
	 * @param newVoices A List of the {@link Voice}s available to have the given {@link MidiNote} added to them.
	 * @return The probability of the given transition.
	 */
	private double getTransitionProb(MidiNote note, int transition, List<Voice> newVoices) {
		double logProb;
		Voice prev, next;

		// For new Voices, we need to add the Voice, and then update the transition value to
		// point to that new Voice so the lower code works.
		if (transition < 0) {
			transition = -transition - 1;
			logProb = Math.log(params.NEW_VOICE_PROBABILITY);
			prev = transition == 0 ? null : newVoices.get(transition - 1);
			next = transition == newVoices.size() ? null : newVoices.get(transition);

		} else {
			logProb = Math.log(newVoices.get(transition).getProbability(note, params));
			prev = transition == 0 ? null : newVoices.get(transition - 1);
			next = transition == newVoices.size() - 1 ? null : newVoices.get(transition + 1);
		}

		// Check if we are in the wrong order with the prev or next Voices (or both)
		if (prev != null && note.getPitch() < prev.getMostRecentNote().getPitch()) {
			logProb -= Math.log(2);
		}

		if (next != null && note.getPitch() > next.getMostRecentNote().getPitch()) {
			logProb -= Math.log(2);
		}

		if (logProb == Double.NEGATIVE_INFINITY) {
			logProb = -Double.MAX_VALUE;
		}

		return logProb;
	}

	/**
	 * Get the {@link Voice}s which are currently contained by this HmmVoiceSplittingModelState.
	 *
	 * @return {@link #voices}
	 */
	@Override
	public List<Voice> getVoices() {
		return voices;
	}

	/**
	 * Get the probability score of this HmmVoiceSplittingModelState. Here, this is the log of the
	 * probability of this state occurring.
	 *
	 * @return {@link #logProb}
	 */
	@Override
	public double getScore() {
		return logProb;
	}

	/**
	 * Get the String representation of this HmmVoiceSplittingModelState, which is simply its
	 * {@link #voices} followed by its {@link #logProb}.
	 *
	 * @return The String representation of this HmmVoiceSPlittingModelState.
	 */
	@Override
	public String toString() {
		return voices.toString() + " " + logProb;
	}

	/**
	 * Compare the given HmmVoiceSplittingModelState to this one and return their difference.
	 * They are ordered first by their {@link #logProb} as returned by {@link #getScore()},
	 * with higher scores coming first. If the scores are equal, the state with the least number
	 * of {@link Voice}s (from {@link #voices}) comes first, and if that is equal, the
	 * {@link Voice}s are compared one by one following their natural ordering from the
	 * {@link Voice#compareTo(Voice)} method.
	 *
	 * @param o The HmmVoiceSplittingModelState we are comparing to.
	 * @return A positive number if this HmmVoiceSplittingModelState should come first, negative
	 * if the given one should come first, or 0 if they are equal.
	 */
	@Override
	public int compareTo(HmmVoiceSplittingModelState o) {
		if (o == null) {
			return -1;
		}

		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}

		result = voices.size() - o.voices.size();
		if (result != 0) {
			return result;
		}

		for (int i = 0; i < voices.size(); i++) {
			result = voices.get(i).compareTo(o.voices.get(i));
			if (result != 0) {
				return result;
			}
		}

		return params.compareTo(o.params);
	}
}

package research.lib;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.lm.ArrayEncodedNgramLanguageModel;
import edu.berkeley.nlp.lm.NgramLanguageModel;
import edu.berkeley.nlp.lm.StringWordIndexer;
import edu.berkeley.nlp.lm.cache.ArrayEncodedCachingLmWrapper;
import edu.berkeley.nlp.lm.io.LmReaders;

/**
 * a wrapper for the Berkeley LM that converts n-gram queries to the appropriate
 * format for querying the lm
 * 
 * @author Courtney Napoles
 * 
 */
public class MyBerkeleyLm {
	NgramLanguageModel<String> lm;
	private double topicFreqCorpus = 384013.14; // freq per 1m; estimated from
										// http://corpus.leeds.ac.uk/internet_pos_en

	/**
	 * load the lm using the Berkeley lm
	 * 
	 * @param lmfile
	 */
	public MyBerkeleyLm(String lmfile) {
		readLmFromFile(lmfile);

		// normalize topic word frequency based on this corpus size
		setTopicFreqCorpus(getTopicFreqCorpus() / 1000000
				* lm.getWordIndexer().numWords());
	}

	/**
	 * decides if the lm is stored in a binary file (based on extensions .b,
	 * .bi, .bin, .binary)
	 * 
	 * @param lmfile
	 */
	private void readLmFromFile(String lmfile) {
		System.err.println("Loading language model from " + lmfile);
		StringWordIndexer swi = new StringWordIndexer();
		NgramLanguageModel<String> ngramLm;
		if (lmfile.endsWith(".b") || lmfile.endsWith(".bi")
				|| lmfile.endsWith(".bin") || lmfile.endsWith("binary")) {
			ngramLm = LmReaders.readLmBinary(lmfile);
		} else {
			ngramLm = LmReaders
					.readArrayEncodedLmFromArpa(lmfile, false, swi);
		}
		lm = ArrayEncodedCachingLmWrapper
				.wrapWithCacheNotThreadSafe((ArrayEncodedNgramLanguageModel<String>) ngramLm);
	}

	// various methods for getting the log probability of an n-gram or a
	// sentence

	public double getLogProb(String s) {
		List<String> ngram = new LinkedList<String>();
		for (String ss : s.split("\\s+"))
			ngram.add(ss);
		return lm.getLogProb(ngram);
	}

	public double getSentenceLogProb(String s) {
		List<String> sentence = new LinkedList<String>();
		for (String ss : s.split("\\s+"))
			sentence.add(ss);
		return lm.scoreSentence(sentence);
	}

	public double getLogProb(String... strings) {
		List<String> ngram = new LinkedList<String>(Arrays.asList(strings));
		return lm.getLogProb(ngram);
	}

	public double getSentenceLogProb(String... strings) {
		List<String> sentence = new LinkedList<String>(Arrays.asList(strings));
		return lm.scoreSentence(sentence);
	}

	public double getLogProb(List<String> s) {
		return lm.getLogProb(s);
	}

	public double getSentenceLogProb(List<String> s) {
		return lm.scoreSentence(s);
	}

	public String endSymbol() {
		return lm.getWordIndexer().getEndSymbol();
	}

	public String startSymbol() {
		return lm.getWordIndexer().getStartSymbol();
	}

	public int getOrder() {
		return lm.getLmOrder();
	}

	public int getWordFrequency(String s) {
		return (int) (lm.getWordIndexer().numWords() * Math
						.exp(getLogProb(s)));
	}

	public void setTopicFreqCorpus(double topicFreqCorpus) {
		this.topicFreqCorpus = topicFreqCorpus;
	}

	public double getTopicFreqCorpus() {
		return topicFreqCorpus;
	}
}
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
	
	/**
	 * load the lm using the Berkeley lm
	 * 
	 * @param lmfile
	 */
	public MyBerkeleyLm(String lmfile) {
		System.err.println("Loading language model from " + lmfile);
		StringWordIndexer swi = new StringWordIndexer();
		ArrayEncodedNgramLanguageModel<String> ngramLm = LmReaders
				.readArrayEncodedLmFromArpa(lmfile, false, swi);
		lm = ArrayEncodedCachingLmWrapper.wrapWithCacheNotThreadSafe(ngramLm);
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
}
package research.compression;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;

import research.lib.*;

/**
 * Compresses sentences subject to several different constraints, such as a
 * target compression rate or length in characters or tokens. For details on the
 * model, see the CompressionModel class.
 * 
 * @author Courtney Napoles
 * 
 */
public class SentenceCompressor {
	// various paths
	String lmModelFile = null; // path to lm model
	String cplexModelFile = null; // optional path to save CPLEX model
	String lengthfile; // optional file containing list of target lengths
	String testfile = null; // path to sentences to compress

	// settings
	boolean strictConstraints = false; // use specific target token length
	boolean charConstraints = false; // use specific target char length
	boolean testLambda = false; // systematically vary lambda value
	boolean twitter = false; // generate a tweet (140 char constraint)
	boolean ngramConstraint = false; // only consider n-grams seen in Google
	String host = "a05"; // hostname of optional lm server
							// n-grams
	boolean rawText = true; // format is raw text (not XML)
	double minCR = 0.4; // minimum compression rate (length output / length
						// input)
	double lambda = 1.4; // weight for significance score
	boolean debug = false;
	boolean quiet = false; // suppress cplex output
	// statistics relevant to sentence being compressed
	// int n,charLength[], b; // n is length of sentence in words, t is length
	// constraint in char, b is length constraint in words
	String pis[];
	HashMap<String,Integer> zeta, targetLengths;
	List<Document> documents;
	// counts etc for significance model

	MyBerkeleyLm lm; // lm for querying n-gram probabilities
	CompressionModel lpp; // this is where the magic happens
	ArrayList<Sentence> testSentences; // list of sentences to compress
	String compression;

	public static void main(String[] args) {
		SentenceCompressor sentenceCompressor = new SentenceCompressor();
		sentenceCompressor.parseOptions(args);

		try {
			sentenceCompressor.initialize();
			sentenceCompressor.compressSentences();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void parseOptions(String[] s)  {
		for (String opt : s) {
			if (opt.equals("-char")) charConstraints = true;
			else if (opt.startsWith("-cr=")) minCR=Double.parseDouble(opt.replace("-cr=",""));
			else if (opt.startsWith("-in=")) testfile = opt.replace("-in=","");
			else if (opt.startsWith("-l="))	lambda = Double.parseDouble(opt.replace("-l=",""));
			else if (opt.startsWith("-lm="))
				lmModelFile = opt.replace("-lm=", "");
			else if (opt.startsWith("-ngram")) ngramConstraint=true;
			else if (opt.startsWith("-target=")) {
				lengthfile = opt.replace("-target=","");
				strictConstraints = true;
			}
			else if (opt.startsWith("-test_lambda")) testLambda = true;
			else if (opt.startsWith("-tweet")) twitter = true;
			else if (opt.startsWith("-xml")) rawText = false;
			else if (opt.startsWith("-debug"))
				debug = true;
			else if (opt.startsWith("-quiet"))
				quiet = true;
			else {
				System.err.println("Invalid option: " + opt);
				System.exit(2);
			}
		}
		if (testfile == null || lmModelFile == null) {
			System.err.println("Must specify input -in=val and language model file -lm=val");
			System.exit(2);
		}
	}

	/**
	 * load
	 * 
	 * @param docs
	 * @throws Exception
	 */
	public void initialize() throws Exception {
		// load the documents
		testSentences = new ArrayList<Sentence>();
		if (debug) {
			Sentence.debug = true;
		}
		documents = new DocumentImporter().loadDocuments(testfile, rawText);
		for (Document d : documents) {
			d.getTopicWordData();
			// save all sents to test sent array
			testSentences.addAll(d.getSentences());
		}
		System.err.println(testSentences.size() + " sents loaded");

		// load the language model
		lm = new MyBerkeleyLm(lmModelFile);

		// read in the length constraints from file
		if (strictConstraints || charConstraints) {
			targetLengths = new HashMap<String,Integer>();
			Scanner in = new Scanner(new File(lengthfile));
			String line;
			String[] vals;
			int count = 0;
			while (in.hasNext()) {
				line = in.nextLine().trim();
				vals = line.split("\t");
				if (vals.length == 1) 
				    targetLengths.put(""+ count,Integer.parseInt(vals[0]));
				else targetLengths.put(vals[0],Integer.parseInt(vals[3]));
				count++;
			}
		}

		// initialize the ILP
		lpp = new CompressionModel(lm, lambda, cplexModelFile, minCR);
		lpp.defineSettings(twitter, strictConstraints, charConstraints,
				ngramConstraint);
		lpp.suppressCplexOutput(quiet);
		lpp.writeSentenceModels(debug);

		// only if using Google n-gram constraint
		if (ngramConstraint) {
			loadZetas();
			lpp.setZetas(zeta);
		}

	}

	/**
	 * boolean indicator if using the Google n-gram constraint (1 if the ngram
	 * appears in Google n-grams, 0 otherwise). Note: requires n-gram server to
	 * be running.
	 */
	public void loadZetas() {
		SocketClient ngramServer = null;
		try {
			ngramServer = new SocketClient(host,8888);
		} catch (Exception e) {
			System.err.println("Error connecting to the n-gram server ("+host+"). Exiting.");
			e.printStackTrace();
			System.exit(-1);
		}
		zeta = new HashMap<String,Integer>();
		for (String s : generateTrigrams()) {
			zeta.put(s,ngramServer.hasNgram(s));
		}
		ngramServer.close();
	}

	/**
	 * generate all possible trigrams from the sentences for querying the n-gram
	 * server (if using Google n-gram constraint)
	 * 
	 * @return
	 */
	public HashSet<String> generateTrigrams() {
		HashSet<String> allNgrams = new HashSet<String>();
		for (Document doc : documents) {
			for (Sentence sent : doc.getSentences()) {
				for (int i = 0; i < sent.length(); i++) {
					allNgrams.add(sent.getToken(i));
					allNgrams.add("<s> " + sent.getToken(i));
					for (int j = i + 1; j < sent.length(); j++) {
						for (int k = j + 1; k < sent.length(); k++) {
							allNgrams
									.add(sent.getToken(i) + " "
											+ sent.getToken(j) + " "
											+ sent.getToken(k));
						}
					}
				}
			}
		}
		return allNgrams;
	}
	/**
	 * solve the ILP
	 * 
	 * @return
	 */
	public String findSolution(Sentence sent) {
		try {
			lpp.initializeVariables(sent);
			lpp.createMaximization();
			lpp.addConstraints();
			lpp.addGlobalConstraints();
			String s = lpp.solve();
			compression = lpp.compression;
			lpp.clear();
			return s;
		}
		catch (Exception e) {
			System.err.println("Error initialization ILP");
			e.printStackTrace();
			return "-1\t<ERROR>";
		}
	}

	/**
	 * for each sentence, calculate relevant statistics and call CPLEX
	 */
	public void compressSentences() {
		System.err.println("Compressing "+testSentences.size()+" sentences...");
		// ArrayList<String> results = new ArrayList<String>();
		HashMap<String,String> compressions;
		String sol= "";
		DecimalFormat df = new DecimalFormat("#.#");
		for (Sentence sent : testSentences) {

			// to test various different values of lambda
			if (testLambda) {
				compressions = new HashMap<String,String>();
				for (lambda = 0.1; lambda <=2.5; lambda+=0.1) {
					sol = findSolution(sent);
					if (compressions.containsKey(sol))
						compressions.put(sol, compressions.get(sol)+" "+df.format(lambda));
					else compressions.put(sol,df.format(lambda));
				}
				for (Entry<String,String> e : compressions.entrySet())
					System.out.println(e.getValue() + "\t"
							+ sol.split("\\s+").length + "\t" + sent.length()
							+ "\t" + e.getKey());
			}

			// if not testing lambda (running as usual)
			else {
				int slength = sent.length() - 1; // because sent contains
				// <s>
				if (strictConstraints || charConstraints) {
					lpp.setTargetLength(targetLengths.get(sent.getId()));
					sol = findSolution(sent);
					if (charConstraints) slength = sent.charLength();

					if (sol.equals("")) continue;
					System.out.println(slength + "\t" + sol + "\t"
							+ targetLengths.get(sent.getId()));
				}
				else {
					sol = findSolution(sent);
					if (sol.equals("")) continue;
					System.out.println(slength + "\t" + sol + "\t"
							+ minCR);
				}
			}
		}
	}

	public String getCompression() {
		return compression;
	}
}

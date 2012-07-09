package research.compression;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

import research.lib.GrammarDependency;
import research.lib.MyBerkeleyLm;
import research.lib.Sentence;
import research.lib.Sentence.Clause;

import ilog.concert.*;
import ilog.cplex.IloCplex;

/**
 * This creates and solves an ILP model for compressing sentences, following
 * Clarke and Lapata, 2008, "Global Inference for Sentence Compression: An
 * Integer Linear Programming Approach". It requires ILOG CPLEX, which can be
 * acquired for free through IBM's academic initiative (license must be renewed
 * each year). The main method is in the SentenceCompressor class.
 * 
 * @author Courtney Napoles
 * 
 */
public class CompressionModel {
	IloCplex cplex;

	// binary variables
	IloIntVar delta[]; // is token i in the compression
	IloIntVar alpha[]; // does token i start the compression
	IloIntVar beta[][]; // does bigram <i,j> end the compression
	IloIntVar gamma[][][]; // is trigram <i,j,k> in the compression
	IloIntVar pi[][]; // is paraphrase j of phrase i in the compression (unused)

	int n; // length of original sentence (tokens)
	int t = 120; // twitter length char constraint (this leaves 20 chars for a
	// short url)
	double lambda = 1.0; // weight for significance score
	HashMap<String, Integer> zetaMap; // for looking up n-grams in Google
	// n-grams

	boolean twitter = false; // use t as a length constraint?
	boolean strictLength = false; // use a strict length constraint?
	boolean strictCharLength = false; // use a character length constraint?
	double minCR = 0.4; // minimum compression rate (cr=compression
	// length/original length)
	boolean paraphrase = false; // paraphrasing not fully implemented/tested
	boolean ngramConstraint = false; // only use n-grams found in Google n-grams
	boolean debug = false;
	MyBerkeleyLm lm;
	String modelFile; // if user wants to save the model output
	Sentence sentence; // sentence being compressed
	String[] sentTokens; // tokens of original sentence
	int b; // target length
	String compression; // output compression

	/**
	 * initialize settings for the ILP solver
	 * 
	 * @param lm
	 * @param twitter
	 * @param lambda
	 * @param strictConstraints
	 * @param charConstraints
	 * @param ngramConstraint
	 * @param modelFile
	 * @param minCR
	 */
	public CompressionModel(MyBerkeleyLm lm, double lambda, String modelFile,
			double minCR) {
		this.lm = lm;
		this.lambda = lambda;
		this.minCR = minCR;
		try {
			cplex = new IloCplex();
		} catch (IloException e) {
			System.err.println("Error loading CPLEX. Exiting");
			System.exit(1);
		}
		cplex.setOut(System.err);
		this.modelFile = modelFile;
	}

	public void defineSettings(boolean twitter, boolean strictConstraints,
			boolean charConstraints, boolean ngramConstraint) {
		this.twitter = twitter;
		this.strictLength = strictConstraints;
		this.strictCharLength = charConstraints;
		this.ngramConstraint = ngramConstraint;
	}

	/**
	 * create variables for this sentence and add to ILP
	 * 
	 * @param s
	 * @throws IloException
	 * @throws FileNotFoundException
	 */
	public void initializeVariables(Sentence s) throws IloException,
			FileNotFoundException {
		n = s.length();
		this.sentence = s;
		sentTokens = sentence.getTokens();
		// create boolean variables
		// delta = 1 if token i is present in the compression, 0 o/w
		delta = new IloIntVar[n];
		for (int i = 0; i < n; i++) {
			delta[i] = cplex.boolVar();
			delta[i].setName("d{" + sentTokens[i] + "_" + i + "}");
		}
		// alpha = 1 if token i starts compression, 0 o/w
		alpha= new IloIntVar[n];
		for (int i = 0; i < n; i++) {
			alpha[i] = cplex.boolVar();
			alpha[i].setName("a{" + sentTokens[i] + "_" + i + "}");
		}
		// beta = 1 if bigram ij ends compression, 0 o/w
		beta = new IloIntVar[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = i+1; j < n; j++) {
				beta[i][j]=cplex.boolVar();
				beta[i][j].setName("b{" + sentTokens[i] + "_" + i + ","
						+ sentTokens[j] + "_" + j + "}");
			}
		}
		// gamma = 1 if trigram ijk is present in compression, 0 o/w
		gamma = new IloIntVar[n][n][n];
		for (int i = 0; i < n; i++)
			for (int j = i+1; j < n; j++)
				for (int k = j+1; k < n;k++) {
					gamma[i][j][k]=cplex.boolVar();
					gamma[i][j][k].setName("g{" + sentTokens[i] + "_" + i + ","
							+ sentTokens[j] + "_" + j + "," + sentTokens[k]
							+ "_" + k + "}");
				}

		// initialize pi if paraphrasing turned on--not implemented
		if (paraphrase) {
			// // System.out.println("<pk>"+sc.phraseKey+"</pk>");
			// String[] unitLength = s.phraseKey.split("\\s+");
			// //
			// System.out.println("there are "+unitLength.length+" pphrases");
			// pi = new IloIntVar[unitLength.length][];
			// for (int i = 0; i < unitLength.length; i++) {
			// pi[i]=new IloIntVar[Integer.parseInt(unitLength[i])];
			// for (int j = 0; j < pi[i].length; j++) {
			// pi[i][j] = cplex.boolVar();
			// pi[i][j].setName("pi{"+i+","+j+"}");
			// }
			// }
		}
	}

	/**
	 * reset model
	 * 
	 * @throws IloException
	 * @throws IOException
	 */
	public void clear() throws IloException, IOException {
		cplex.setOut(null);
		cplex.clearModel();
	}


	/**
	 * create the objective function
	 * 
	 * @throws Exception
	 */
	public void createMaximization() throws Exception {
		IloLinearNumExpr objfn = cplex.linearNumExpr();
		// add variables with lm probability
		for (int i = 1; i < n; i++) {
			objfn.addTerm(lm.getLogProb(sentTokens[0], sentTokens[i]),
					alpha[i]);
		}
		for (int i = 1; i < n-2; i++) {
			for (int j = i+1; j < n-1; j++) {
				for (int k = j+1; k < n; k++) {
					objfn.addTerm(lm.getLogProb(sentTokens[i],
							sentTokens[j], sentTokens[k]),
							gamma[i][j][k]);
				}
			}
		}
		for (int i = 0; i < n-1; i++) {
			for (int j = i+1; j < n; j++) {
				objfn.addTerm(lm.getLogProb(sentTokens[i],
						sentTokens[j], "</s>"), beta[i][j]);
			}
		}

		// add significance score
		for (int i = 1; i < n; i++) {
			double d = getSigScore(i);
			if (d != 0.0)
				objfn.addTerm(d * lambda, delta[i]);
		}
		cplex.addMaximize(objfn);
	}

	public double getSigScore(int i) {
		double d = 0;
		if (sentence.isTopicWord(i)) {
			d = 1.0 * sentence.getDepth(i) / sentence.getHeight();
			d *= sentence.getFrequency(i);
			d *= Math.log(lm.getTopicFreqCorpus()
					/ lm.getWordFrequency(sentTokens[i]));
			if (debug) {
				System.err.println("lp(" + sentTokens[i] + ") = " + lm.getLogProb(sentTokens[i]));
				System.err.println("sig(" + sentTokens[i] + ") = " + d + " = "
						+ sentence.getDepth(i) + "/" + sentence.getHeight()
						+ " * " + sentence.getFrequency(i) + " * log("
						+ lm.getTopicFreqCorpus() + "/"
						+ lm.getWordFrequency(sentTokens[i]) + ")");
			}
		}
		return d;
	}

	/**
	 * add constraints to the ILP
	 * 
	 * @throws IloException
	 */
	public void addConstraints() throws IloException {
		IloLinearIntExpr expr = cplex.linearIntExpr();
		b = (int) ((n - 1) * minCR);
		if (b < 2) b=2;
		if (b > n-1) b = n-1;

		// Length Constraint
		if (strictCharLength) {
			expr.clear();
			for (int i = 1; i < n; i++)
				expr.addTerm(sentence.getCharLength(i), delta[i]);
			cplex.addLe(expr, b + 5, "char length constraint");
			cplex.addGe(expr, b - 5, "char length constraint");
		}
		else if (twitter) {
			expr.clear();
			for (int i = 1; i < n; i++)
				expr.addTerm(sentence.getCharLength(i), delta[i]);
			cplex.addLe(expr,t,"twitter length constraint");
			expr.clear();
			int minLength = (int) (sentence.charLength() * 0.4);
			if (minLength >= t) {
				minLength = (int) (t * 0.1);
			}
			for (int i = 1; i < n; i++) {
				expr.addTerm(sentence.getCharLength(i), delta[i]);
			}
			cplex.addGe(expr, minLength, "twitter length constraint");
		}
		else if (strictLength) {
			expr.clear();
			for (int i = 1; i < n; i++)
				expr.addTerm(1,delta[i]);
			cplex.addGe(expr, b - 1, "length constraint");
			cplex.addLe(expr, b + 1, "length constraint");
		}
		else {
			expr.clear();
			for (int i = 1; i < n; i++)
				expr.addTerm(1,delta[i]);
			cplex.addGe(expr, b, "length constraint");
		}
		expr.clear();

		// Constraint 1 - exactly one token starts compression
		for (int i = 1; i < n; i++)
			expr.addTerm(1,alpha[i]);
		cplex.addEq(expr,1,"constraint 1");

		// Constraint 2 - every token in compression must either
		// follow two tokens, follow <s> and a token, or start compression
		for (int k = 1; k < n; k++) {
			expr.clear();
			expr.addTerm(1, delta[k]);
			expr.addTerm(-1,alpha[k]);
			for (int i = 0; i < k-1; i++)
				for (int j = i+1; j < k;j++)
					expr.addTerm(-1,gamma[i][j][k]);
			cplex.addEq(expr,0,"constraint 2");
		}

		// Constraint 3 - every token in compression must be preceded by a
		// token and be followed by another token or </s>
		for (int j = 1; j < n; j++) {
			expr.clear();
			expr.addTerm(1,delta[j]);
			for (int i = 0; i < j; i++)
				for (int k = j+1; k <n; k++)
					expr.addTerm(-1,gamma[i][j][k]);
			for (int i = 0; i < j; i++)
				expr.addTerm(-1,beta[i][j]);
			cplex.addEq(expr,0,"constraint 3");
		}

		// Constraint 4 - every token in compression must be followed by
		// two tokens or one token and </s>, or it is preceded by one token and
		// followed by </s>
		for (int i = 1; i<n; i++) {
			expr.clear();
			expr.addTerm(1,delta[i]);
			for (int j = i+1; j < n-1; j++)
				for (int k = j+1; k < n;k++)
					expr.addTerm(-1,gamma[i][j][k]);
			for (int j = i+1; j < n; j++)
				expr.addTerm(-1,beta[i][j]);
			for (int h = 0; h < i; h++)
				expr.addTerm(-1,beta[h][i]);
			cplex.addEq(expr,0,"constraint 4");
		}

		// Constraint 5 - exactly one bigram can end a compression
		expr.clear();
		for (int i = 0; i < n-1; i++)
			for (int j = i+1; j < n; j++)
				expr.addTerm(1,beta[i][j]);
		cplex.addEq(expr,1,"constraint 5");


		// // add paraphrase constraints
		// if (paraphrase && !sc.phraseKey.equals("")) {
		// // constraint 1: <= one pphrase per slot
		// for (int i = 0; i < pi.length; i++) {
		// expr.clear();
		// for (int j = 0 ; j < pi[i].length; j++) {
		// expr.addTerm(1,pi[i][j]);
		// }
		// cplex.addLe(expr,1,"paraphrase constraint 1");
		// }
		//
		// // constraint 2: all or nothing for words in a paraphrase
		// expr.clear();
		// int[] inds = new int[2];
		// int slot=1, id=1;
		// int c = 0;
		// for (int i = 1; i < n; i++) {
		// if (!sc.pis[i].equals("0,0")) { // this word is the part of a
		// paraphrase
		// inds[0] = Integer.parseInt(sc.pis[i].split(",")[0]);
		// inds[1] = Integer.parseInt(sc.pis[i].split(",")[1]);
		// if (inds[0] != slot && inds[1]!=id) {
		// // close out old expression
		// expr.addTerm(-1*c,pi[slot-1][id-1]);
		// cplex.addEq(expr,0,"paraphrase constraint 2");
		// expr.clear();
		// c=0;
		// }
		// slot=inds[0];
		// id = inds[1];
		// c++;
		// expr.addTerm(1,delta[i]);
		// }
		// }
		// }

		// only allow n-grams in compression present in the Google n-grams
		if (ngramConstraint) {
			for (int i = 1; i < n-2; i++)
				for (int j = i+1; j < n-1; j++)
					for (int k = j+1; k < n; k++) {
						cplex.addEq(
								gamma[i][j][k],
								zetaMap.get(sentTokens[i] + " "
										+ sentTokens[j] + " "
										+ sentTokens[k]),
								"ngram constraint " + i + "-" + j + "-" + k);
					}
		}
	}

	/**
	 * Perform optimization and return the resulting compression
	 * 
	 * @return
	 */
	public String solve() {
		String output="";
		String indices = "";
		try {
			cplex.solve();
			int len=0;
			String deltas = "";
			StringBuffer sb = new StringBuffer();
			ArrayList<String> solution = new ArrayList<String>();
			for (int i = 1; i < n; i++) {
				deltas+=cplex.getValue(delta[i])+" ";
				if (cplex.getValue(delta[i])>=0.9) { // because sometimes "binary" values are 0.999999 or 1.000001 etc.
					solution.add(sentence.getOriginal(i));
					sb.append(i);
					sb.append(" ");
					len++;
				}
			}
			output = makePretty(solution);
			indices = sb.toString().trim();
			compression = output;
			if (strictCharLength)
				len = output.length();
			if (!output.equals(""))
				output = len+"\t"+output+"\t"+indices;
		} catch (Exception e) {
			System.err.println("ERROR: no solution exists");
			e.printStackTrace();
		}
		try {
			if (cplex.getStatus() != IloCplex.Status.Optimal) {
				System.err.println("ERROR: no optimal solution found");
				writeModel();
			}
		} catch (IloException e) {
			System.err.println("ERROR: unable to determine CPLEX status");
		}
		if (debug)
			writeModel();
		return output;
	}

	/**
	 * Make sure the first letter of the sentence is capitalized and there is
	 * sentence-final punctuation.
	 * 
	 * @param sentTokens
	 * @return
	 */
	private String makePretty(ArrayList<String> sentTokens) {
		// if a sentence is missing sentence-final punctuation, add a period to
		// the end of the sentence
		char c;
		for (int i = sentTokens.size() - 1; i >= 0; i--) {
			c = sentTokens.get(i).charAt(0);
			if (c == '.' || c == '?' || c == '!') {
				break;
			}
			if (c != '\'' && !sentence.isQuotationMark(sentTokens.get(i))) {
				sentTokens.add (".");
				break;
			}
		}
		StringBuffer sb = new StringBuffer();
		for (String s : sentTokens) {
			sb.append(s);
			sb.append(" ");
		}
		String s = sb.toString().trim();
		// ensure capitalization of first word
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			if (c >= 65 && c <= 122) {
				if (c <= 90)
					break;
				if (c >= 97) {
					s = Character.toUpperCase(c) + s.substring(i + 1);
					break;
				}
			}
		}
		return s;
	}

	/**
	 * write the model to file
	 */
	public void writeModel() {
		try {
			cplex.exportModel(modelFile + "-" + sentence.getId() + ".lp");
			System.err.println("Cplex model saved to " + modelFile + "-"
					+ sentence.getId() + ".lp");
		} catch (Exception e) {}
	}

	/**
	 * add grammatical constraints. See Clarke and Lapata (2008) for details.
	 * 
	 * @throws IloException
	 */
	public void addGlobalConstraints() throws IloException {
		int[] conjunctions = new int[n];
		IloLinearIntExpr expr = cplex.linearIntExpr();

		// add constraints for Stanford grammar dependencies
		for (GrammarDependency gr : sentence.getDependencies()) {
			if (gr.isMod()) {
				cplex.addGe(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"mod constraint");
			}
			else if (gr.isDet()) {
				cplex.addGe(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"det constraint");
			}
			else if (gr.isPoss()) {
				cplex.addEq(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"poss constraint");
			}
			else if (gr.isNeg()) {
				cplex.addEq(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"neg constraint");
			}
			else if (gr.isSubjObj()) {
				cplex.addEq(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"subj/obj constraint");
			}
			else if (gr.isPpSub()) {
				cplex.addEq(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0,"rev pp constraint");
			}
			else if (gr.isType("cc")) {
				conjunctions[gr.a()] = gr.b();
				cplex.addGe(cplex.sum(delta[gr.a()],cplex.negative(delta[gr.b()])),0);
				//create equations to keep track of the conjuncts of a
			}
			else if (gr.isType("conj")) {
				cplex.addGe(cplex.sum(delta[gr.b()],cplex.negative(delta[conjunctions[gr.a()]])),0);
				cplex.addGe(cplex.sum(delta[conjunctions[gr.a()]],cplex.negative(delta[gr.b()]),cplex.negative(delta[gr.b()])),-1);
			}

			// add constraints for PPs and SBARs
			for (Clause c : sentence.getClauses()) {
				int i = c.getHead();
				expr.clear();
				for (int j : c.getConstituents()) {
					cplex.addGe(cplex.sum(delta[i],cplex.negative(delta[j])),0,"pp/sbar constraint");
					expr.addTerm(1, delta[j]);
				}
				expr.addTerm(-1, delta[i]);
				cplex.addGe(expr,0,"pp/sbar constraint");
			}
		}

		// add constraint that there must be >= 1 non-punctuation token
		expr.clear();
		for (int i = 1; i < n - 1; i++) {
			if (sentTokens[i].matches("\\w+")) {
				expr.addTerm(1, delta[i]);
			}
		}
		cplex.addGe(expr, 1, "punctuation constraint");

		// at least one verb must be in the compression if a verb is in the original sentence
		boolean found = false;
		expr.clear();
		for (int i = 1; i < n; i++) {
			if (sentence.isVerb(i)) {
				expr.addTerm(1,delta[i]);
				found = true;
			}
		}
		if (found)
			cplex.addGe(expr,1,"verb constraint");
		expr.clear();

		for (int i = 1; i < n; i++) {
			// don't include any tokens in parentheses
			if (sentence.inParens(i)) {
				cplex.addEq(delta[i],0,"bracket constraint");

			}
			// include all personal pronouns (bad rule?)
			else if (sentence.isPRP(i)) {
				cplex.addEq(delta[i],1,"prp constraint");
			}
		}
	}

	public void setTargetLength(Integer i) {
		b = i;
	}

	public void setZetas(HashMap<String, Integer> zetaMap) {
		this.zetaMap = zetaMap;
	}

	public void suppressCplexOutput(boolean b) {
		if (b)
			cplex.setOut(null);
	}

	public void writeSentenceModels(boolean b) {
		debug = b;

	}

}

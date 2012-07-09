package research.lib;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * represents a sentence and various information about it, including surface
 * form, parse, dependency structure, and various statistics for sentence
 * compression.
 * 
 * @author Courtney Napoles
 * 
 */
public class Sentence {

	public static boolean debug = false;

	// parses Stanford dependency output, $1 = rel, $2 = index a, $3 = index b
	static final Pattern depPattern = Pattern
			.compile("([a-z]+)\\(\\S+-([0-9]+),\\s\\S+-([0-9]+)\\)");
	// matches a tuple (VP xxx), $1 = VP, $2 = xxx
	static final Pattern parsePattern = Pattern.compile("\\((\\S+) (\\S+?)\\)");

	// used for outline generation only:
	// verbs should be in the document or the OutlineGenerator class,
	// since these are not an intrinsic property of sentences
	// and because they need to be the same across sentences
	static HashSet<String> verbSet = null;
	static HashMap<String,Integer> verbLookup = null;
	static int currIndex = 0;

	// location of morpha (for outline generation)
	static File morphDir = null;
	static Runtime r = null;

	// used for compression and outline generation
	// information about this sentence
	String text, parse = null, depParse = null;
	String id;
	String tokens[], original[], pos[], verbs[], pi[], verbsInSent;
	boolean parens[]; // is token i in brackets?
	boolean punct[]; // is token i punctuation?
	boolean edges[][]; // is there a dependency between tokens i and j?
	int len, originalLength; // len = originalLength + 1 (for <s>)
	int height = 0; // deepest level of embedding
	int depth[]; // = -1 if not a topic word, o/w the first index of the (A x)
	List<GrammarDependency> dependencies; // grammatical relations in this
	// sentence
	LinkedList<Clause> clauses; //for PPs and SBARs
	Document document = null; // if this sentence belongs to a document
	// Every sentence starts at index 0. index -1 is the start node <s>
	int[] charLength; // length of each token

	public Sentence() {}

	/**
	 * initialize the features of this sentence
	 */
	public void initialize() {
		boolean inputIsParsed = true;
		text = text.trim();
		if (parse == null || depParse == null) {
			inputIsParsed = false;
		}
		if (inputIsParsed) {
			extractTokens();
		}
		String[] temp = text.split("\\s+");
		len = temp.length+1;
		original = new String[len];
		tokens = new String[len];
		original[0] = "<s>";
		tokens[0] = "<s>";
		for (int i = 1; i < len; i++) {
			original[i] = temp[i-1];
			tokens[i] = temp[i - 1].toLowerCase();
			// change form of quotation marks to match those in the Gigaword lm
			if (isQuotationMark(temp[i - 1])) {
				tokens[i] = "\"";
			}
		}
		charLength = new int[len];
		for (int i = 1; i < len; i++) {
			charLength[i] = tokens[i].length() + 1; // each token length
			// includes a space
			// following the token
		}

		if (inputIsParsed) {
			extractPOSTags();
			loadDependencies();
			findParens();
		}
		if (debug) {
			System.err.println("TEXT\t" + text);
			System.err.println("PARSE\t" + parse);
			System.err.println("DEPPARSE\t" + depParse);
			String orig = "ORIG\t";
			String tokenlist = "TOKS\t";
			String chars = "CHARS\t";
			String postags = "POS\t";
			String puncts = "PUNCT\t";
			String brackets = "PARENS\t";
			String header = "\t";
			for (int i = 0; i < len; i++) {
				header += i + "\t";
				orig += original[i] + "\t";
				tokenlist += tokens[i] + "\t";
				chars += charLength[i] + "\t";
				postags += pos[i] + "\t";
				puncts += punct[i] + "\t";
				brackets += parens[i] + "\t";
			}
			System.err.println(header);
			System.err.println(orig);
			System.err.println(tokenlist);
			System.err.println(chars);
			System.err.println(postags);
			System.err.println(puncts);
			System.err.println(brackets);
		}

	}

	/**
	 * extract tokens from the sentence parse
	 */
	public void extractTokens() {
		text = "";
		Matcher m = parsePattern.matcher(parse);
		int i = 0;
		StringBuilder sb = new StringBuilder();
		while (m.find(i)) {
			sb.append(m.group(2));
			sb.append(" ");
			i = m.end(2);
		}
		text = sb.toString().trim();
	}

	/**
	 * determine the part of speech of all tokens, and whether they are verbs or punctuation
	 */
	public void extractPOSTags() {
		pos = new String[len];
		punct = new boolean[len];
		pos[0] = "<S>";
		punct[0] = false;
		int index = 1;
		Matcher m = parsePattern.matcher(parse);
		int i = 0;
		char c;
		StringBuffer sb = new StringBuffer();

		while (m.find(i)) {
			pos[index] = m.group(1);
			i = m.end(1);
			c = pos[index].charAt(0);
			if (c == 'V' || c == 'v') {
				sb.append(tokens[index]);
				sb.append("_V ");
			}
			if (c == '.' || c == ',' || c == ':' ) {
				punct[index] = true;
			}
			index++;
		}
		verbsInSent = sb.toString();
	}

	/**
	 * initialize morpha for lemmatization
	 */
	public void initializeMorpha() {
		String morphaFile = null;
		URL dirUrl = getClass().getClassLoader().getResource("research/lib/DocumentImporter.class"); // get my directory
		URL fileUrl = null;
		// super hacky...
		if (dirUrl.getPath().contains("jar")) {
			morphaFile = dirUrl.getPath().substring(dirUrl.getPath().indexOf(':')+1,dirUrl.getPath().lastIndexOf('!'));
			morphaFile = morphaFile.substring(0,morphaFile.lastIndexOf('/'))+"/data/englishPCFG.ser.gz";
		}
		else {
			try {
				fileUrl = new URL(dirUrl, "../../../lib/morph");
			} catch (MalformedURLException e) {
				System.err.println("Error finding morpha. Please set path manually.");
				System.exit(1);
			} // get a related file
			morphaFile = fileUrl.getPath();
		}

		if (morphDir == null) {
			morphDir = new File(morphaFile);
			r = Runtime.getRuntime();
		}
	}

	/**
	 * returns the stem of the verb
	 * @throws IOException
	 */
	public void findStems() throws IOException {
		if (morphDir == null) initializeMorpha();
		Process p = r.exec(new String[]{"sh","-c","echo '"+verbsInSent+"' | ./morpha"},null,morphDir);
		BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = "";
		StringBuffer sb = new StringBuffer();
		while ((line = input.readLine()) != null) {
			sb.append(line);
			sb.append(" ");
		}
		input.close();

		verbs = new String[len];
		Arrays.fill(verbs,"");
		int index = 1;
		for (String stem : sb.toString().split("\\s")) {
			while (!isVerb(index)) index++;
			verbs[index++] = stem;
		}
	}

	/**
	 * load the dependency graph
	 */
	public void loadDependencies() {
		edges = new boolean[len][len];
		Matcher m = depPattern.matcher(depParse);
		dependencies = new LinkedList<GrammarDependency>();
		int i = 0;

		while (m.find(i)) {
			dependencies.add(new GrammarDependency(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
			edges[Integer.parseInt(m.group(2))][Integer.parseInt(m.group(3))] = true;
			i = m.end();
		}
		Collections.sort(dependencies);
	}

	/**
	 * find PPs and SBARs in the sentence
	 */
	public void findClauses() {
		clauses = new LinkedList<Clause>();
		LinkedList<ParseTree> toExplore = new LinkedList<ParseTree>();
		toExplore.add(new ParseTree(parse, null));
		while (!toExplore.isEmpty()) {
			ParseTree temp = toExplore.removeFirst();
			if (temp.getChildren() != null)
				toExplore.addAll(temp.getChildren());
			if (temp.getLabel().equalsIgnoreCase("PP")
					|| temp.getLabel().equalsIgnoreCase("SBAR")) {
				Clause c = new Clause();
				if (c.init(temp.bracketed, temp.begin, temp.end))
					clauses.add(c);
			}
		}
	}

	/**
	 * for each token, calculate its level of embedding
	 */
	public void calculateDepth() {
		String clauses = parse;
		String temp = "";
		depth = new int[len];

		while (!temp.equals(clauses)) {
			temp = clauses;
			clauses = clauses.replaceAll("\\(S ([^\\(]*?)\\)", "<<< $1 >>>");
			clauses = clauses.replaceAll("\\((\\S{2,}|[^S]) ([^\\(]*?)\\)",
					"$2");
		}
		String[] bp = clauses.split("\\s+");
		int j = 0;
		String depths = "DEPTH\t-1\t";
		for (int i = 1; i < len; i++) {
			while (j < bp.length && !tokens[i].equalsIgnoreCase(bp[j]))
				j++;
			int d = 0;
			for (int k = 0; k < j; k++) {
				if (bp[k].equals("<<<"))
					d++;
				else if (bp[k].equals(">>>"))
					d--;
			}
			depth[i] = d;
			depths += depth[i] + "\t";
		}

		if (debug)
			System.err.println(depths);
		height = 1;
		for (int i = 1; i < len; i++)
			if (depth[i] > height)
				height = depth[i];
	}

	/**
	 * represents a clause that can be deleted for compression
	 * 
	 * @author Courtney Napoles
	 * 
	 */
	public class Clause {
		int head;
		int[] constituents;
		HashSet<String> allowableTags = new HashSet<String>(
				Arrays.asList("TO,WDT,IN,WP,WP$,WRB,IN".split(",")));

		public boolean init(String s, int start, int end) {
			ArrayList<Integer> items = new ArrayList<Integer>();
			Matcher m = parsePattern.matcher(s);
			int c = 0;
			int i = start;
			while (m.find(c)) {
				if (c == 0) {
					if (allowableTags.contains(m.group(1)))
						return false;
				}
				c = m.end(1);
				if (punct[i])
					return false;
				if (tokens[i].matches("\\w+"))
					items.add(i);
				// don't include verbs in subordinate clauses in constraint that
				// there must be at least one verb in sentence
				// if (isVerb(i)) verbs[i]=-1;
				i++;
			}

			if (items.size() == 0 && debug) {
				System.err.println("no clauses");
				// return false;
			}
			head = items.get(0);
			constituents = new int[items.size() - 1];
			for (i = 1; i < items.size(); i++)
				constituents[i - 1] = items.get(i);
			return true;
		}

		public int[] getConstituents() {
			return constituents;
		}

		public int getHead() {
			return head;
		}
	}

	/**
	 * determine whether each token is within brackets
	 */
	public void findParens() {
		parens = new boolean[len];
		boolean inParens = false;
		for (int i = 1; i < parens.length; i++) {
			if (inParens) {
				parens[i] = true;
				if (tokens[i].equals(")"))
					inParens = false;
			} else {
				if (tokens[i].equals("(")) {
					inParens = true;
					parens[i] = true;
				} else
					parens[i] = false;
			}
		}
	}

	// various lookups
	public boolean isVerb(int i) {
		return pos[i].charAt(0) == 'V' || pos[i].charAt(0) == 'v';
	}

	public boolean isPRP(int i) {
		return pos[i].equals("PRP");
	}

	public boolean isNoun(int i) {
		return pos[i].charAt(0) == 'N';
	}

	public boolean inParens(int i) {
		return parens[i];
	}

	public boolean isTopicWord(int i) {
		return isVerb(i) || isNoun(i);
	}

	public boolean isParens(int i) {
		return parens[i];
	}

	public boolean isPunct(int i) { return punct[i]; }

	/**
	 * checks if a string is a PTB-style quotation mark
	 * 
	 * @param s
	 * @return
	 */
	public boolean isQuotationMark(String s) {
		if (s.length() == 2) {
			if (s.charAt(0) == '\'' && s.charAt(0) == '\'')
				return true;
			else if (s.charAt(0) == '`' && s.charAt(0) == '`')
				return true;
		}
		return false;
	}

	public boolean hasEdge(int i, int j) { return edges[i][j]; }


	// various setters and getters
	public void setTokens(String tokens[]) { this.tokens = tokens; }

	public String[] getTokens() { return tokens; }

	public void setPos(String pos[]) { this.pos = pos; }

	public String[] getPosTags() { return pos; }

	public String getPos(int i) { return pos[i]; }

	public void setDepParse(String s) { depParse = s; }

	public void setParse(String s) { parse = s; }

	public void setText(String s) { text = s; }

	public void setId(String s) { id = s; }

	public String getVerb(int i) { return verbs[i]; }

	public List<GrammarDependency> getDependencies() { return dependencies; }

	public String getToken(int i) { return tokens[i]; }

	public int length() { return len; }
	public int charLength() { return text.length(); }

	public String[] getOriginal() { return original; }

	public String getParse() { return parse; }

	public String getId() {
		if (document == null || document.getTitle().equals(""))
			return id;
		return document.getTitle()+"."+id;
	}

	public String getOriginal(int i) {
		return original[i];
	}

	public double getFrequency(int i) {
		if (!document.topicWordFreq.containsKey(tokens[i])) {
			return 0.1;
		}
		return document.topicWordFreq.get(tokens[i]);
	}

	public String getText() { return text; }

	public void setPi(String[] s) { pi = s;	}

	public void setLength() {
		originalLength = len;
		len = tokens.length;
	}

	public String[] getPi() { return pi; }

	public LinkedList<Clause> getClauses() { return clauses; }

	public double getOrigLength() { return originalLength; }


	public int getDepth(int i) { return depth[i]; }

	public int getHeight() { return height; }

	public String[] getVerbs() { return verbs; }
	public void setParent(Document d) {
		document = d;
	}

	public int getCharLength(int i) {
		return charLength[i];

	}

	public static void resetVerbs() {
		verbSet = null;
	}
}

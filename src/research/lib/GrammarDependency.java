package research.lib;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * represents a Stanford-style grammar dependency: its head, dependent, and type
 * of relation. required for grammatical sentence compression
 * 
 * @author Courtney Napoles
 * 
 */
public class GrammarDependency implements Comparable<GrammarDependency> {

	// list of dependencies (Stanford dependnecy types)
	static final HashSet<String> requiredRels = new HashSet<String>(Arrays.asList("acomp advmod aux auxpass ccomp cop det dobj mark mwe neg poss possessive prt xcomp".split("\\s")));
	static final HashSet<String> args = new HashSet<String>(Arrays.asList("csubj csubjpass dobj nsubj nsubjpass".split("\\s")));
	static final HashSet<String> mods = new HashSet<String>(Arrays.asList("acomp advmod amod aux auxpass cop infmod measure nn num number partmod prep prt quantmod".split("\\s")));
	static final HashSet<String> subj_obj = new HashSet<String>(Arrays.asList("dobj expl iobj nsubj xsubj attr".split("\\s")));
	static final HashSet<String> poss = new HashSet<String>(Arrays.asList("poss possessive".split("\\s")));
	static final HashSet<String> pp_sub= new HashSet<String>(Arrays.asList("advcl ccomp complm csubj csubjpass mark pcomp pobj prep prepc purpcl rcmod ref rel tmod xcomp".split("\\s")));

	static final Pattern depPattern = Pattern.compile("([a-z]+)\\(\\S+-([0-9]+),\\s\\S+-([0-9]+)\\)"); // group1 = rel, group2= index a, group3=indexb
	Matcher m;

	String type; // what dependency is this?
	int a; // governor index
	int b; // dependent index

	/**
	 * initialize grammar dependency
	 */
	public GrammarDependency(String s, int a, int b) {
		type = s;
		this.a = a;
		this.b = b;
	}
	public GrammarDependency() {}

	/**
	 * parse a grammar dependnecy from a string (of format
	 * "name(word-i, word-j)")
	 * 
	 * @param s
	 */
	public void parse(String s) {
		m = depPattern.matcher(s);
		m.find();
		type = m.group(1);
		a = Integer.parseInt(m.group(2));
		b = Integer.parseInt(m.group(3));
	}

	/**
	 * return true if this relation requires the head and dependent to stay together
	 */
	public boolean isRequiredRel() { return requiredRels.contains(type); }

	/**
	 * returns true if this relation is between a verb and one of its arguments
	 * @return
	 */
	public boolean isArg() { return args.contains(type); }

	/**
	 * comparator over String types
	 */
	@Override
	public int compareTo(GrammarDependency o) { return (type.compareTo(o.type)); }

	/**
	 * get index of governor
	 * 
	 * @return
	 */
	public int a() {
		return a;
	}

	/**
	 * get index of dependent
	 * 
	 * @return
	 */
	public int b() {
		return b;
	}

	public boolean isType(String s) {
		return type.equals(s);
	}

	public boolean isPpSub() {
		return pp_sub.contains(type);
	}

	public boolean isMod() {
		return mods.contains(type);
	}

	public boolean isNeg() {
		return isType("neg");
	}
	public boolean isDet() {
		return isType("det");
	}

	public boolean isSubjObj() {
		return subj_obj.contains(type);
	}

	public boolean isPoss() {
		return poss.contains(type);
	}

	public boolean isConj() {
		return isType("conj");
	}

	public boolean isCc() {
		return isType("cc");
	}

	/**
	 * set governor
	 * 
	 * @param i
	 */
	public void setA(int i) {
		a = i;
	}

	/**
	 * set dependent
	 * 
	 * @param i
	 */
	public void setB(int i) {
		b = i;
	}
}
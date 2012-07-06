package research.lib;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * hacky implementation of a parse tree
 * 
 * @author Courtney Napoles
 * 
 */
public class ParseTree {
	int height,begin,end, current=0;
	ParseTree parent, root;
	ArrayList<ParseTree> children;
	String label, value, bracketed;
	boolean isLeaf=false, hasParaphrase=false;
	Pattern pLabel = Pattern.compile("\\((\\S*)\\s+");
	Pattern pValue = Pattern.compile("\\(\\S+\\s+(\\S+)\\)");
	ArrayList<List<ParseTree>> nodesByDepth;


	public int depth() { return nodesByDepth.size(); }

	public int getBegin() { return begin; }
	public int getEnd() { return end; }
	public String getLabel() { return label; }
	public ArrayList<ParseTree> getChildren() { return children; }

	public List<ParseTree> nodesAtDepth(int i) {
		return nodesByDepth.get(i);
	}

	public String getLabeledString() {
		//		System.out.println(label+": "+begin+"-"+end);
		if (isLeaf) return begin+"-"+label+"-"+value;
		String s = "";
		ArrayList<String> temp = new ArrayList<String>();
		for (ParseTree c : children)
			temp.add(c.getLabeledString());
		for (String t : temp)
			s+=t+" ";
		return s.trim();
	}

	public String toString() {
		//		System.out.println(label+": "+begin+"-"+end);
		if (isLeaf) return value;
		String s = "";
		ArrayList<String> temp = new ArrayList<String>();
		if (children==null) return "";
		for (ParseTree c : children)
			temp.add(c.toString());
		for (String t : temp)
			s+=t+" ";
		return s.trim();
	}

	public ParseTree(String s, ParseTree parent) {
		bracketed = s;
		this.parent = parent;
		if (parent==null) {
			height = 0;
			root = this;
			nodesByDepth=new ArrayList<List<ParseTree>>();
		}
		else {
			height = parent.height+1;
			root = parent.root;
		}
		if (root.nodesByDepth.size() <= height) {
			root.nodesByDepth.add(height, new LinkedList<ParseTree>());
		}
		root.nodesByDepth.get(height).add(this);
		parse(s);
	}

	public void parse(String s) {
		s = s.trim();
		//		System.out.println("EVAL="+s);
		if (s.matches("\\(\\S+\\s+\\S+\\)")) {
			setLeaf(s);
			return;
		}
		if (s.length() == 0 || s.charAt(0)!='(' || s.charAt(s.length()-1)!=')') {
			System.err.println("error parsing string\t"+s);
			return;
		}
		isLeaf=false;
		Matcher m = pLabel.matcher(s);
		m.find();
		//		System.out.println("label="+m.group(1));
		label = m.group(1);
		String temp = s.substring(s.indexOf('(', 1),s.length()-1);
		//		System.out.println(temp);
		children = new ArrayList<ParseTree>();
		while (temp.length() > 0) {
			int count=0, i=1;
			char[] c = temp.toCharArray();
			if (c[0]=='(') count++;
			for ( ; i < c.length; i++) {
				//				System.out.println("i="+i+",c="+count);
				if (count==0) break;
				if (c[i]=='(') count++;
				if (c[i]==')') count--;
			}
			//			System.out.println("first () ended at "+i);
			setChild(temp.substring(0,i));
			temp = temp.substring(i).trim();
		}
		begin = children.get(0).begin;
		end = children.get(children.size()-1).end;
		//		System.out.println(value+"\t"+label+" "+begin+"\t"+end);
	}

	public void setChild(String substring) {
		//		System.out.println("child: "+substring);
		children.add(new ParseTree(substring,this));
	}

	public void setLeaf(String substring) {
		root.current +=1;
		begin=root.current;
		end = root.current;
		Matcher m = pLabel.matcher(substring);
		isLeaf=true;
		m.find();
		label = m.group(1);
		m = pValue.matcher(substring);
		m.find();
		value = m.group(1);
		//		System.out.println("LEAF="+label+"_"+value);
	}

	public int getSize() {
		if (isLeaf) return 1;
		int i = 0;
		if (children == null) return 0;
		for (ParseTree c : children)
			i+=c.getSize();
		return i;
	}

	public String getSpan() {
		if (isLeaf) return value;
		String s = "";
		for (ParseTree c : children) 
			s += c.getSpan();
		return s;
	}
}

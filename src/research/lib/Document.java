package research.lib;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * represents a document, which is fundamentally a collection of sentences.
 * useful for processing context-aware sentences.
 * 
 * @author Courtney Napoles
 * 
 */
public class Document extends DefaultHandler {
	ArrayList<Sentence> sentences;
	Sentence tempSent;
	String tempVal;
	String title = null;
	String headline= null;
	String type = null;
	String context; // this is the list of verb stems present in the document
	boolean ignore = false;
	HashMap<String,Double> topicWordFreq;

	public Document() {	sentences = new ArrayList<Sentence>(); }

	public Document(List<Sentence> sents) {	sentences = new ArrayList<Sentence>(sents);	}

	/**
	 * parse the document from a XML file
	 */
	public Document(String file) throws FileNotFoundException {
		sentences = new ArrayList<Sentence>();
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			sp.parse(file, this);
		} catch(Exception e) {
			System.err.println("Error parsing document "+file);
		}
		//System.err.println("Loaded "+sentences.size()+" sents");
	}

	public void add(Sentence s) { sentences.add(s); }

	// methods for parsing XML

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if(qName.equalsIgnoreCase("sentence")) {
			Sentence temp = new Sentence();
			tempSent = temp;
			tempVal = "";
			if (attributes != null)
				tempSent.id = attributes.getValue("id");
		}
		else if (qName.equalsIgnoreCase("compression")) {
			ignore = true;
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		tempVal = new String(ch,start,length);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (qName.equalsIgnoreCase("sentence")) {
			tempSent.initialize();
			tempSent.setParent(this);
			sentences.add(tempSent);
		}
		else if (qName.equalsIgnoreCase("title")) {
			setTitle(tempVal);
		}
		else if (qName.equalsIgnoreCase("headline")) {
			headline = tempVal;
		}
		else if (qName.equalsIgnoreCase("type")) {
			type = tempVal;
		}
		else if (qName.equalsIgnoreCase("id")) {
			tempSent.id = tempVal;
		}
		else if (qName.equalsIgnoreCase("text") || qName.equalsIgnoreCase("original")) {
			tempSent.text = tempVal;
		}
		else if (qName.equalsIgnoreCase("compression")) {
			ignore = false;
		}
		if (ignore) return;

		else if (qName.equalsIgnoreCase("parse") || qName.equalsIgnoreCase("bracket_parse")) {
			tempSent.parse = tempVal;
		}
		else if (qName.equalsIgnoreCase("dep_parse")) {
			tempSent.depParse = tempVal;
		}
	}

	public List<Sentence> getSentences() { return sentences; }

	public void setTitle(String s) {
		if (s.endsWith(".xml")) title = s.replaceFirst(".xml","");
		else title = s;
	}

	public String getTitle() { return title; }

	/**
	 * prompt sentences to find topic words
	 * 
	 * @throws Exception
	 */
	public void getTopicWordData() throws Exception {
		topicWordFreq = new HashMap<String,Double>();
		for (Sentence s : sentences) {
			for (int i = 0; i < s.length(); i++) {
				if (s.isTopicWord(i)) {
					double count = topicWordFreq.containsKey(s.getToken(i)) ? topicWordFreq.get(s.getToken(i)) : 0;
					topicWordFreq.put(s.getToken(i), count + 1);
				}
			}
			s.loadDependencies();
			s.findClauses();
			s.calculateDepth();
		}
	}

	/**
	 * return list of topic words from all sentences in the document
	 * 
	 * @return
	 */
	public Set<String> getTopicWords() {
		return topicWordFreq.keySet();
	}

	public void setContext(String s) { context = s; }
	public String getContext() { return context; }
}
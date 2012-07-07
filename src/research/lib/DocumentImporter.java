package research.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Filters;

/**
 * Load several documents from file (raw text or XML) and create Document and
 * Sentence representations storing various information about the documents.
 * Uses the Stanford Parser.
 * 
 * @author Courtney Napoles
 * 
 */
public class DocumentImporter {

	List<Document> documents;
	LexicalizedParser lexParser = null;
	TreebankLanguagePack tlp;
	GrammaticalStructureFactory gsf;
	// need to specify the path to the grammar file

	public DocumentImporter() {
		documents = new ArrayList<Document>();
	}

	public void loadParser() {
		if (lexParser != null) return; // already loaded grammar
		URL grammarFile = getClass().getClassLoader().getResource(
				"research/lib/englishPCFG.ser.gz");
		lexParser = new LexicalizedParser(grammarFile.getFile());
		tlp = new PennTreebankLanguagePack();
		gsf = tlp.grammaticalStructureFactory(Filters.<String>acceptFilter());
	}

	/**
	 * load a file or all documents from a directory
	 * 
	 * @param filename
	 *            filepath or directory
	 * @param rawText
	 *            F for xml, T otherwise
	 * @return
	 * @throws IOException
	 */
	public List<Document> loadDocuments(String filename, boolean rawText) throws IOException {
		File data = new File(filename);
		if (data.isFile()) {
			Document temp;
			if (rawText) {
				temp = loadRawText(filename);
				generateParses(temp);
			}
			else temp = new Document(filename);
			temp.setTitle(filename.substring(filename.lastIndexOf('/')+1,filename.length()));
			documents.add(temp);
		}
		else {
			for (String s : data.list()) {
				if (s.charAt(0)== '.') continue;
				Document temp;
				if (rawText) {
					temp = loadRawText(filename+"/"+s);
					generateParses(temp);
				}
				else temp = new Document(filename+"/"+s);
				temp.setTitle(s);
				documents.add(temp);
			}
		}
		return documents;
	}

	/**
	 * Create sentences from a raw-text document (i.e. no SGML markup)
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public Document loadRawText(String filename) throws IOException {
		Document doc = new Document();
		BufferedReader input = new BufferedReader(new FileReader(filename));
		String line;

		int count = 0;
		while ( (line=input.readLine()) != null ) {
			Sentence tempSent = new Sentence();
			tempSent.text = line.trim();
			tempSent.setId(""+(count++));
			doc.add(tempSent);
			tempSent.document = doc;
		}
		input.close();
		return doc;
	}

	/**
	 * parse all sentences in a document using the Stanford Parser
	 * 
	 * @param doc
	 */
	public void generateParses(Document doc) {
		loadParser();
		List<Word> tokenized;
		GrammaticalStructure gs;
		Collection<TypedDependency> tdl;

		for (Sentence s : doc.getSentences()) {
			if (s.depParse == null || s.parse == null) {
				tokenized = new DocumentPreprocessor(PTBTokenizerFactory.newWordTokenizerFactory("")).getWordsFromString(s.getText());
				lexParser.parse(tokenized);
				gs = gsf.newGrammaticalStructure(lexParser.getBestParse());
				tdl = gs.typedDependencies();
				s.setDepParse(tdl.toString());
				s.setParse(lexParser.getBestParse().toString().replaceAll("\\[\\S+?\\]\\s", ""));
				s.setText(join(tokenized));
			}
			s.initialize();
		}
	}

	public String join(List<Word> l) {
		StringBuilder sb = new StringBuilder();
		for (Object o : l) {
			sb.append(o.toString());
			sb.append(" ");
		}
		return sb.toString();
	}
}
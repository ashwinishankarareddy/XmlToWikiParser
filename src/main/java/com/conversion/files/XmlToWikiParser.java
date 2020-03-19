package com.conversion.files;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.jdom2.input.DOMBuilder;

public class XmlToWikiParser {

	static final Logger logger = Logger.getLogger(XmlToWikiParser.class);

	static int headingLevel = 1;
	public static final String HELP = "\nArguments: " + "\n  <input folder> <output folder>" + "\n\nwhere:"
			+ "\n  <input folder>          input folder: folder which has xml files"
			+ "\n  <output folder>         output folder: folder to where wiki file to be created" + "\n\n";

	public static void main(String[] args) {

		if (args.length != 2) {
			System.out.println(HELP);
			return;
		}

		String inputFolder = args[0];
		String outputFolder = args[1];

		// processses xml files in input folder and generate biqui files.
		processFiles(inputFolder, outputFolder);
		// Creating an instance of WatchService which monitors the input folder
		// for new file.
		startWatcher(inputFolder, outputFolder);
	}

	private static void startWatcher(String inputFolder, String outputFolder) {
		try {
			// Creates an instance of WatchService.
			WatchService watcher = FileSystems.getDefault().newWatchService();

			// Registers the logDir below with a watch service.
			Path logDir = Paths.get(inputFolder);
			logger.info("Watching " + inputFolder + " for new files");
			logDir.register(watcher, ENTRY_CREATE);

			// Monitor the logDir at listen for change notification.
			while (true) {
				WatchKey key = watcher.take();
				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();

					if (ENTRY_CREATE.equals(kind)) {
						logger.info("Found new file");
						processFiles(inputFolder, outputFolder);
					}
				}
				key.reset();
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void processFiles(String inputFolder, String outputFolder) {

		try {
			String[] xmlFiles = new File(inputFolder).list();

			for (String xmlFilePath : xmlFiles) {

				File xmlFile = new File(inputFolder + xmlFilePath);
				logger.info("Processing File: " + xmlFilePath);
				Document document = getStAXParsedDocument(xmlFile);

				Element rootNode = document.getRootElement();

				String output = "";

				List<Content> elements = rootNode.getContent();

				output = processContentList(elements, output);
				String normalizedOutput = output.replaceAll("\n\n", "\n");
				createAndWriteWiki(outputFolder, normalizedOutput, xmlFile.getName());

				// delete processed xml files in input folder
				if (xmlFile.delete()) {
					logger.info("File deleted successfully: " + xmlFilePath);
				} else {
					logger.error("Failed to delete the file: " + xmlFile.getName());
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void createAndWriteWiki(String outputFolder, String output, String fileName) throws IOException {
		String wikiFileName = fileName.replaceAll(".xml", ".biqui");
		File file = new File(outputFolder + wikiFileName);

		if (file.createNewFile()) {
			logger.info("File created: " + wikiFileName);
		} else {
			logger.error("File already exists: " + wikiFileName);
		}

		// Write Content
		FileWriter writer = new FileWriter(file);
		writer.write(output);
		writer.close();

	}

	private static String processContentList(List<Content> contentList, String output) {
		for (Content content : contentList) {
			switch (content.getCType()) {
			case Text:
				String normalizedText = ((Text) content).getTextNormalize();
				if (!normalizedText.isEmpty()) {
					if (((Text) content).getParent().getName().equals("section")) {
						output = output + "\n" + normalizedText + "\n";
					} else {
						output = output + normalizedText;
					}
				}
				break;
			case Element:
				output = processElement((Element) content, output);
				break;
			default:
				break;
			}
		}
		return output;
	}

	private static String processElement(Element element, String output) {
		switch (element.getName()) {
		case "section":
			output = processSection(element, output);
			break;
		case "bold":
			output = processBold(element, output);
			break;
		case "italic":
			output = processItalic(element, output);
			break;
		default:
			break;
		}
		return output;
	}

	private static String processSection(Element element, String output) {
		String markup = getHeadingMarkup(headingLevel);
		output = output + "\n" + markup + element.getAttributeValue("heading") + markup + "\n";

		if (element.getContent().size() > 0) {
			headingLevel++;
			output = processContentList(element.getContent(), output);
		}
		headingLevel--;
		return output;
	}

	private static String processBold(Element element, String output) {
		output = output + "'''";
		if (element.getContent().size() > 0) {
			output = processContentList(element.getContent(), output);
		}
		return output + "'''";
	}

	private static String processItalic(Element element, String output) {
		output = output + "''";
		if (element.getContent().size() > 0) {
			output = processContentList(element.getContent(), output);
		}
		return output + "''";
	}

	private static String getHeadingMarkup(int level) {
		String markup = "";
		if (level >= 6) {
			markup = "======";
		} else {
			for (int i = 0; i < level; i++) {
				markup += "=";
			}
		}
		return markup;
	}

	private static Document getStAXParsedDocument(File fileName) {
		Document document = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// If want to make namespace aware.
			// factory.setNamespaceAware(true);
			DocumentBuilder documentBuilder = factory.newDocumentBuilder();
			org.w3c.dom.Document w3cDocument = documentBuilder.parse(fileName);
			document = new DOMBuilder().build(w3cDocument);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return document;
	}
}

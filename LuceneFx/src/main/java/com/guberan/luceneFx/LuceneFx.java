package com.guberan.luceneFx;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * LuceneFx
 * 
 * minimal JavaFx application to demo Apache's Lucene search engine with Tika conversion.
 */
public class LuceneFx extends Application implements Initializable
{
	// constants key name
	public static final String PREF_INDEX_PATH = "index_path";
	public static final String PREF_DOC_PATH = "doc_path";
	public static final String PREF_REINDEX = "reindex";
	public static final String KEY_CONTENTS = "contents";
	public static final String INDEX_DIR_NAME = ".lucene_index";
	
	private static final Logger log = LoggerFactory.getLogger(LuceneFx.class);
	
	// static resources and application object
	private static ResourceBundle i18nBundle = ResourceBundle.getBundle("com.guberan.luceneFx.ResourceBundle");
	// application reference
	private static LuceneFx app;

	// fx controls 
	@FXML protected TextField searchText;
	@FXML protected TableView<ResultDoc> tbl;
	@FXML protected Button btnSearch;
	
	// current result list
	ObservableList<ResultDoc> resultList = FXCollections.observableArrayList();
	
	// indexes and other object for lucene
	private Directory luceneDir;
	private IndexReader indexReader;
	private IndexSearcher searcher;	
	private Analyzer analyzer;
	private QueryParser parser;
	
	// clipboard
	private Clipboard systemClipboard = Clipboard.getSystemClipboard();
	
	// Edit menu items
	@FXML protected MenuItem mItemCut;
	@FXML protected MenuItem mItemCopy;
	@FXML protected MenuItem mItemPaste;
	@FXML protected MenuItem mItemDelete;
	@FXML protected MenuItem mItemSelectAll;	

	// fx properties
	private final SimpleObjectProperty<Path> docPathProp = new SimpleObjectProperty<>(this, "docPath");
	private final SimpleObjectProperty<Path> indexPathProp = new SimpleObjectProperty<>(this, "indexPath");
	private final SimpleBooleanProperty reindexProp = new SimpleBooleanProperty(this, "reindex");
	private final SimpleIntegerProperty maxResultsProp = new SimpleIntegerProperty(this, "maxResults", 1000);
	
	public SimpleObjectProperty<Path> docPathProperty() { return docPathProp; }
	public SimpleObjectProperty<Path> indexPathProperty() { return indexPathProp; }
	public SimpleBooleanProperty reindexProperty() { return reindexProp; }
	public SimpleIntegerProperty maxResultsProperty() { return maxResultsProp; }

	public Path getDocPath() { return docPathProp.get(); }	
	public void setDocPath(Path newDocDir) { docPathProp.set(newDocDir); }
	public Path getIndexPath() { return indexPathProp.get(); }
	public void setIndexPath(Path Index) {  indexPathProp.set(Index); }
/*
	public int getMaxResults() { return maxResultsProp.get(); }
	public void setMaxResults(int newValue) { maxResultsProp.set(newValue); }

	public boolean getReindex() { return reindexProp.get(); }
	public void setReindex(boolean newValue) { reindexProp.set(newValue); }
*/
	public static LuceneFx getApp() { return app; }
	
	
	
	/**
	 * save preferences using java.util.prefs package
	 */
	public void savePreferences() 
	{
		Preferences prefs = Preferences.userNodeForPackage(LuceneFx.class);	
		prefs.put(PREF_DOC_PATH, getDocPath().toString());
		prefs.put(PREF_INDEX_PATH, getIndexPath().toString());
		prefs.put(PREF_REINDEX, String.valueOf(reindexProperty().get()));
	}
	
	
	/**
	 * openIndex
	 */
	public void openIndex(boolean rebuildIndex)
	{	
		try {
			if (luceneDir != null)
				luceneDir.close();
			
			if (getIndexPath().toFile().exists())  {
				// open current index directory
				luceneDir = FSDirectory.open(getIndexPath());
			}
			else if (rebuildIndex && !getIndexPath().toString().isEmpty()) {
				// will create new index here
				luceneDir = FSDirectory.open(getIndexPath());
			}
			else if (getDocPath().toFile().exists()) {
				// memory index
				luceneDir = new RAMDirectory();
				rebuildIndex = true; // force index rebuild
			}
			else {
				// no index nor document directory !
				// disable search button in GUI
				btnSearch.setDisable(true);
				return;
			}
			
			if (rebuildIndex && getDocPath().toFile().exists()) {
				reIndex();
			}
			
			indexReader = DirectoryReader.open(luceneDir);
			searcher = new IndexSearcher(indexReader);
			analyzer = new NoAccentAnalyzer();
			parser = new QueryParser(KEY_CONTENTS, analyzer);			
			
			resultList.clear();
			btnSearch.setDisable(false);
		}
		catch (IOException e) {
			showException(e);
		}	
	}
	

	/**
	 * Opens a progress dialog and re-index documents
	 */
	public void reIndex() throws IOException
	{
		
		IndexTask indexTask = new IndexTask(getDocPath(), getIndexPath(), luceneDir);		
		new Thread(indexTask).start();

		loadFxmlInStage("Progress", false, (ProgressController cntr) -> cntr.setTask(indexTask));
	}

	
	/**
	 * application init
	 */
	@Override
	public void init() 
	{
		app = this;
	}
	
	
	/**
	 * javafx controller initialization
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) 
	{
		// set result list
		tbl.setItems(resultList);
	}


	/**
	 * application start
	 */
	@Override
	public void start(Stage stage) throws IOException
	{
		// load scene from fxml
		FXMLLoader fxmlLoader = new FXMLLoader(LuceneFx.class.getResource("LuceneFx.fxml"));
		fxmlLoader.setController(this);			
		stage.setScene(new Scene(fxmlLoader.load()));
		stage.setTitle(tr("LuceneFx.stageName"));
		stage.getIcons().addAll(
				new Image(LuceneFx.class.getResourceAsStream("icon32.png")),
				new Image(LuceneFx.class.getResourceAsStream("icon22.png")),
				new Image(LuceneFx.class.getResourceAsStream("icon16.png")));
		stage.show();
		
		// read preferences
		Preferences prefs = Preferences.userNodeForPackage(LuceneFx.class);
		setIndexPath(Paths.get(prefs.get(PREF_INDEX_PATH, "")));
		setDocPath(Paths.get(prefs.get(PREF_DOC_PATH, "")));
		reindexProperty().set(Boolean.parseBoolean(prefs.get(PREF_REINDEX, "")));
		
		// open pref dialog if there are no preferences  
		if (!getIndexPath().toFile().exists() && !getDocPath().toFile().exists())
			onPref();
		else
			openIndex(reindexProperty().get());	
	}

	
	
	/**
	 * show exception to user
	 * 
	 * @param e the exception to display
	 */
	public void showException(Exception e)
	{
		// print stack 
		e.printStackTrace();

		Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Error Dialog");
		alert.setHeaderText("An error occured.");
		alert.setContentText(e.getMessage());

		// Create expandable Exception.
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String exceptionText = sw.toString();

		TextArea textArea = new TextArea(exceptionText);
		textArea.setEditable(false);
		textArea.setWrapText(true);

		textArea.setMaxWidth(Double.MAX_VALUE);
		textArea.setMaxHeight(Double.MAX_VALUE);
		GridPane.setVgrow(textArea, Priority.ALWAYS);
		GridPane.setHgrow(textArea, Priority.ALWAYS);

		GridPane expContent = new GridPane();
		expContent.setMaxWidth(Double.MAX_VALUE);
		expContent.add(textArea, 0, 0);

		// Set expandable Exception into the dialog pane.
		alert.getDialogPane().setExpandableContent(expContent);
		alert.showAndWait();		
	}

	
	/**
	 * search with Lucene
	 * 
	 * @param text  text to search
	 * @throws IOException
	 */
	public void search(String text) throws IOException
	{
		try {
			// parse search string
			Query query = parser.parse(text);
	
			// Collect search results
			TopDocs results = searcher.search(query, maxResultsProperty().get());
			ScoreDoc[] hits = results.scoreDocs;

			// show results in TableView
			resultList.clear();
			
			for (ScoreDoc hit : hits) {				
				Document doc = searcher.doc(hit.doc);
				resultList.add(new ResultDoc(doc, hit.score));
			}
		}
		catch (ParseException ex) {
			// if the text could not be parsed, clear search result 
			// but do not bring an error dialog.
			// parsing an empty or blank string throws a parser Exception			
			ex.printStackTrace();
			resultList.clear();
			java.awt.Toolkit.getDefaultToolkit().beep();
		}
		
	}
	

	/**
	 * handle search command
	 * Triggered by button click or enter key in search text field
	 */
	@FXML public void onSearch(ActionEvent a)
	{
		try {
			if (!btnSearch.isDisabled())
				search(searchText.getText());
			
		} catch (Exception e) {
			showException(e);
		}		
	}


	/**
	 * Opens preference dialog
	 */
	@FXML public void onPref()
	{
		PrefController cntl = (PrefController) loadFxmlInStage("Pref", false, (PrefController p) -> { } );
		
		if (cntl.getResultOK())
			openIndex(reindexProperty().get());
	}
	
	
	/**
	 * open selected document in desktop
	 */
	protected void openSelection() 
	{		
		try {
	    	ResultDoc selection = tbl.getSelectionModel().getSelectedItem();
	    	if (selection != null)
	    		Desktop.getDesktop().open(new File(selection.pathProperty().get()));
		} catch (IOException e) {
			showException(e);
		}
	}
	
	
	/**
	 * a line in TableView was clicked, open the document in desktop
	 * @param event MouseEvent
	 */
	@FXML public void onTableMouseClicked(MouseEvent event)
	{
		if (event.getClickCount() >= 2 && MouseButton.PRIMARY.equals(event.getButton())) {
			openSelection();
			event.consume();
		}
	}
	
	
	/**
	 * a line in TableView is dragged, copy a link to the document
	 * @param event MouseEvent
	 */
	@FXML public void onDragDetected(MouseEvent event)
	{
		ResultDoc selection = tbl.getSelectionModel().getSelectedItem();
		if (selection != null) {
			Dragboard db = tbl.startDragAndDrop(TransferMode.COPY, TransferMode.LINK);
			ClipboardContent content = new ClipboardContent();
			File f = new File(selection.pathProperty().get());
			content.putFiles(Collections.singletonList(f));
			db.setContent(content);
			event.consume();
		}
	}
	
	
	/**
	 * Return or Enter was pressed in the TableView
	 * @param event KeyEvent
	 */
	@FXML public void onKeyTyped(KeyEvent event)
	{
		if ("\r".equals(event.getCharacter()) || "\n".equals(event.getCharacter())) {
			openSelection();
			event.consume();
		}
	}
	
	
	/**
	 * onAbout
	 */
	@FXML public void onAbout(ActionEvent a)
	{
		Alert alert = new Alert(AlertType.INFORMATION);
		alert.setTitle(tr("About.title"));
		Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
		stage.getIcons().add(new Image(this.getClass().getResourceAsStream("icon16.png")));
		alert.setHeaderText(null);
		alert.setContentText(tr("About.info"));
		alert.showAndWait();		
	}
	
	
	/**
	 * respond to Quit command
	 */
	@FXML public void onQuit(ActionEvent a)
	{
		Platform.exit();
	}
	

	/* ============================================================================================
	   edit menu
	============================================================================================ */

	/**
	 * return the TextInputControl currently in focus
	 */
	private TextInputControl getFocusedTextField() 
	{		
		Node n = searchText.getScene().getFocusOwner();
		if (n instanceof TextInputControl)
			return (TextInputControl) n;
		
		return null;
	}

		
	/**
	 * Menu Edit is being shown
	 */
	@FXML public void onShowingEdit() 
	{	
		TextInputControl focusedField = getFocusedTextField();
		String txt = (focusedField == null) ? "" : focusedField.getSelectedText();
		boolean noTxt = (txt == null) || (txt.length() == 0);
		
		mItemCut.setDisable(noTxt);
		mItemCopy.setDisable(noTxt);
		mItemPaste.setDisable((focusedField == null) || !systemClipboard.hasString());
		mItemDelete.setDisable(noTxt);
		mItemSelectAll.setDisable(focusedField == null);
	}

	
	/**
	 * on cut menu
	 */
	@FXML public void onCut(ActionEvent a)
	{
		TextInputControl focusedField = getFocusedTextField();
		if (focusedField != null)
			focusedField.cut();
	}
	
	
	/**
	 * on copy menu
	 */
	@FXML public void onCopy(ActionEvent a)
	{
		TextInputControl focusedField = getFocusedTextField();
		if (focusedField != null)
			focusedField.copy();
	}

	/**
	 * on paste menu
	 */
	@FXML public void onPaste(ActionEvent a)
	{
		TextInputControl focusedField = getFocusedTextField();
		if (focusedField != null)
			focusedField.paste();
	}

	/**
	 * on delete menu
	 */
	@FXML public void onDelete(ActionEvent a)
	{
		TextInputControl focusedField = getFocusedTextField();
		if (focusedField != null) {
			IndexRange range = focusedField.getSelection();
			focusedField.deleteText(range);
		}
	}

	/**
	 * on selectAll menu
	 */
	@FXML public void onSelectAll(ActionEvent a)
	{
		TextInputControl focusedField = getFocusedTextField();
		if (focusedField != null)
			focusedField.selectAll();
	}

	
	/**
	 * utility method to load fxml file in a Stage, then showAndWait.
	 * 
	 * @param fxmlName name of fxml file to load (without extension)
	 * @param resizable true if window must be resizable
	 * @param initAction extra action to be performed on the controller
	 * @return the controller T created by FXMLLoader, that may contain interesting results
	 */
	protected <T> T loadFxmlInStage(String fxmlName, boolean resizable, Consumer<T> initAction)
	{
		try {			
			FXMLLoader fxmlLoader = new FXMLLoader(LuceneFx.class.getResource(fxmlName + ".fxml"));

			Stage stage = new Stage();
			stage.initModality(Modality.APPLICATION_MODAL);
			stage.setScene(new Scene( fxmlLoader.load()));
			stage.setTitle(tr(fxmlName + ".stageName"));
			stage.setResizable(resizable);
			//stage.getIcons().add(I18n.getImage("app.icon"));
			
			// run passed initAction on controller
			T controller = fxmlLoader.getController();
			initAction.accept(controller);
						
			stage.showAndWait();
			
			return controller;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
		
	/**
	 * Fetch resource translation for the given key. 
	 * if the key is not found, returns the provided defaultValue. (second argument)
	 * Once the resource translation is found, format the string with provided parameters
	 * 
	 * @param key the key used to get the internationalization string
	 * @param defaultValue the default value returned if no translation could be found
	 * @param params the parameters used in the format of the internationalization string
	 * @return the string in the current application language
	 */
	public static String tr(String key, String defaultValue, Object... params) 
	{
		String str;
		try {
			str = i18nBundle.getString(key);
		} catch (Exception e) {
			str = defaultValue;
		}
		
		// if the default value was null, returns the key, otherwise format will crash
		return (str == null) ? key : String.format(str, params);
	}


	/**
	 * tr translate a String or property
	 * 
	 * @param key the key used to get the internationalization string
	 * @return the string in the current application language
	 */
	public static String tr(String key) 
	{
		try { 
			return i18nBundle.getString(key);
		} catch (Exception e) {
			return key;
		}
	}
	
	
	/**
	 * main method
	 * 
	 * @param args
	 */
	public static void main(String[] args)
	{
		launch(args);
	}


	/**
	 * inner class to store results
	 */
	public static class ResultDoc
	{
		private final SimpleStringProperty path;
		private final SimpleStringProperty title;
		private final SimpleFloatProperty score;

		public SimpleStringProperty pathProperty() { return path; }
		public SimpleStringProperty titleProperty() { return title; }
		public SimpleFloatProperty scoreProperty() { return score; }


		public ResultDoc(Document doc, float score) 
		{
			this.path = new SimpleStringProperty(this, "path", doc.get("path"));
			this.title = new SimpleStringProperty(this, "title", doc.get("subject"));
			this.score = new SimpleFloatProperty(this, "score", score);
		}
	}
	
}
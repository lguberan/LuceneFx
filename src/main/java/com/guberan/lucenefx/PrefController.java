package com.guberan.lucenefx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;


/**
 * controller for preference dialog
 */
public class PrefController implements Initializable {
    @FXML
    protected TextField txtDoc;
    @FXML
    protected TextField txtIndex;
    @FXML
    protected CheckBox cbxAutoUpdate;

    private boolean resultOK;


    /**
     * initialize
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        txtDoc.setText(LuceneFx.getApp().docPathProperty().get().toString());
        txtIndex.setText(LuceneFx.getApp().indexPathProperty().get().toString());
        cbxAutoUpdate.setSelected(LuceneFx.getApp().reindexProperty().get());
    }


    /**
     * onChooseDoc
     */
    @FXML
    public void onChooseDoc(ActionEvent ignoredA) {
        boolean updated = chooseDir(txtDoc, LuceneFx.tr("Pref.chooseDocDir"));

        // set default index location based  on document location
        if (updated /*&& txtIndex.getText().isEmpty())*/) {
            txtIndex.setText(txtDoc.getText() + File.separator + LuceneFx.INDEX_DIR_NAME);
        }
    }


    /**
     * onChooseIndex
     */
    @FXML
    public void onChooseIndex(ActionEvent ignoredA) {
        chooseDir(txtIndex, LuceneFx.tr("Pref.chooseIndexDir"));
    }


    /**
     * chooseDir
     *
     * @param field TextField txtDoc or txtIndex
     * @param title title for dialog box
     * @return true if a directory was selected
     */
    public boolean chooseDir(TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);

        File defaultDir = new File(field.getText());
        if (defaultDir.exists())
            chooser.setInitialDirectory(defaultDir);
        else {
            if (defaultDir.getParent() != null)
                defaultDir = new File(defaultDir.getParent());
            if (defaultDir.exists())
                chooser.setInitialDirectory(defaultDir);
            else {
                chooser.setInitialDirectory(new File("."));
            }
        }

        File selectedDir = chooser.showDialog(getStage());

        if (selectedDir != null)
            field.setText(selectedDir.getPath());

        return (selectedDir != null);
    }


    /**
     * onCancel
     */
    @FXML
    public void onCancel(ActionEvent ignoredA) {
        resultOK = false;
        getStage().close();
    }


    /**
     * onOK
     */
    @FXML
    public void onOK(ActionEvent ignoredA) {
        // save prefs;
        LuceneFx.getApp().docPathProperty().set(Paths.get(txtDoc.getText()));
        LuceneFx.getApp().indexPathProperty().set(Paths.get(txtIndex.getText()));
        LuceneFx.getApp().reindexProperty().set(cbxAutoUpdate.isSelected());
        LuceneFx.getApp().savePreferences();

        resultOK = true;
        getStage().close();
    }


    /**
     * @return current stage
     */
    protected Stage getStage() {
        return (Stage) txtDoc.getScene().getWindow();
    }


    /**
     * getResultOK
     * <p>
     * returns true if user clicked the ok button
     */
    public boolean getResultOK() {
        return resultOK;
    }

}

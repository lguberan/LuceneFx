package com.guberan.lucenefx;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;


/**
 * controller for Progress.fxml
 * <p>
 * Reusable class that displays the progress of any javafx.concurrent.Task
 */
public class ProgressController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    @FXML
    protected Label lblInfo;
    @FXML
    protected Button btnAbort;
    @FXML
    protected ProgressBar progressBar;

    protected Task<?> task;


    @FXML
    public void onAbort(ActionEvent a) {
        log.debug("Worker '{}' cancelled.", a.getSource());
        task.cancel();
        close();
    }


    public void onSuccess(WorkerStateEvent event) {
        log.debug("Worker '{}' succeded.", event.getSource());
        task = null;
        close();
    }


    public void onFailed(WorkerStateEvent event) {
        log.error("Worker '{}' failed.", event);
        task = null;
        close();
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        progressBar.setProgress(0.0);
    }


    /**
     * set the task to be monitored
     *
     * @param task Task to be monitored
     */
    public void setTask(Task<?> task) {
        this.task = task;
        task.setOnSucceeded(this::onSuccess);
        task.setOnFailed(this::onFailed);

        progressBar.progressProperty().bind(task.progressProperty());
        lblInfo.textProperty().bind(task.messageProperty());
    }


    /**
     * close stage
     */
    public void close() {
        try {
            Stage stage = (Stage) progressBar.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            // continue if stage is null or if we can't close
        }
    }
}
	

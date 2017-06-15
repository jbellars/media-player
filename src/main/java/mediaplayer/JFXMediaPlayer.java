/**
 * Copyright (c) 2016 by Justin Bellars under MIT License
 */
package mediaplayer;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mediaplayer.controller.JFXMediaPlayerController;

/*
 * JavaFx Media Player example (cobbled together from various sources)
 * See Chapter 7 of Apress "JavaFX 8 Introduction by Example" book
 * and the Apress "Learn JavaFX 8" book
 * as well as https://youtu.be/bWl98dhvf8Q "How to make a movie player using Java / JavaFX" by Wichit Sombat
 */

public class JFXMediaPlayer extends Application
{
    private static final Logger logger = LoggerFactory.getLogger(JFXMediaPlayer.class);

    private static final String STYLESHEET = "/css/media-player.css";
    private static final String FXML = "/fxml/MediaPlayer.fxml";

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 420;

    private Stage primaryStage = null;
    private String styleSheet = null;

    /**
     * Setups and shows application stage.
     *
     * @param primaryStage
     *         Main stage to display
     * @throws Exception
     *         for any exception
     */
    @Override
    public void start(final Stage primaryStage) throws Exception
    {
        try
        {
            logger.debug("Starting application stage");

            this.primaryStage = primaryStage;

            // Load stylesheet
            styleSheet = getClass().getResource(STYLESHEET).toExternalForm();

            // Setup controller, view (FXML)
            JFXMediaPlayerController controller = new JFXMediaPlayerController();
            controller.setMainApp(this);
            URL fxmlUrl = getClass().getResource(FXML);
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl, null);
            fxmlLoader.setController(controller);
            Parent root = fxmlLoader.load();

            // Setup scene, stage
            Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT, Color.TRANSPARENT);
            primaryStage.setScene(scene);
            primaryStage.setTitle("JavaFX Media Player");
            primaryStage.getIcons().add(new Image("/images/media-player-icon.png"));
            primaryStage.initStyle(StageStyle.DECORATED);
            primaryStage.centerOnScreen();

            controller.setupSceneEventHandlers();

            // Show main stage
            primaryStage.show();
        }
        catch (Exception ex)
        {
            logger.warn("Unexpected exception in FX start method", ex);

            // Throwing this exception will cause the application to stop
            throw ex;
        }
    }

    /**
     * Gets primary stage for application.
     *
     * @return Primary stage
     */
    public Stage getPrimaryStage()
    {
        return primaryStage;
    }

    /**
     * Returns Cascading Style-Sheet for main application.
     *
     * @return Cascading Style-Sheet for main application
     */
    public String getStylesheet()
    {
        return styleSheet;
    }

    /**
     * Main entry to application.
     *
     * @param args
     *         Command-line arguments
     */
    public static void main(String[] args)
    {
        try
        {
            logger.info("Starting Video Player");
            launch(args);
            logger.info("Closing Video Player");
        }
        catch (Exception ex)
        {
            logger.warn("Exception caught in main", ex);
            ex.printStackTrace();
        }
    }
}

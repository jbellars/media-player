/**
 * Copyright (c) 2016 by Justin Bellars under MIT License
 */
package mediaplayer.controller;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import mediaplayer.JFXMediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for MediaPlayer
 */
public class JFXMediaPlayerController
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final static String TIME_FORMAT = "%02d:%02d:%02d";

    private JFXMediaPlayer mainApp;  // Reference to main application
    private MediaPlayer mediaPlayer;
    private Point2D anchorPt;
    private Point2D previousLocation;
    private ChangeListener<Duration> progressListener;
    private SimpleDoubleProperty elapsedSecondsProperty = new SimpleDoubleProperty();

    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private MediaView mvMediaView;
    @FXML
    private Slider sldSlider;
    @FXML
    private Button btnPlay;
    @FXML
    private Button btnFfwd;
    @FXML
    private Button btnRewind;
    @FXML
    private Button btnSkipFwd;
    @FXML
    private Button btnSkipBkwd;
    @FXML
    private Button btnStop;
    @FXML
    private Label lblTimeElapsedAndRemaining;

    private static int numSeekPoints = 3;

    private List<Double> seekPointsList;

    private static String convertSecondsToHhMmSs(double seconds)
    {
        long secondsLong = (long) seconds;
        long s = secondsLong % 60;
        long m = (secondsLong / 60) % 60;
        long h = (secondsLong / (60 * 60)) % 24;
        return String.format(TIME_FORMAT, h, m, s);
    }

    /**
     * Initializes scene event handlers.
     */
    public void setupSceneEventHandlers()
    {
        try
        {
            // Initialize stage to be movable via mouse
            initMovablePlayer();

            // Configure the media view for the application
            configMediaView();

            // Initialize stage for various clicks to scene
            initMouseClickMeanings();

            // Create event handlers for buttons
            setupButtonEventHandlers();

            // Configure position slider
            configSlider();

            // Update slider and elapsed seconds as video is progressing
            progressListener = (observable, oldValue, newValue) ->
            {
                sldSlider.setValue(newValue.toSeconds());
                elapsedSecondsProperty.set(newValue.toSeconds());
            };

            // Initializing to accept files dragged over surface to load media
            initFileDragNDrop();
        }
        catch (Exception ex)
        {
            logger.warn("Could not setup scene event handlers", ex);
        }
    }

    /**
     * Initializes controller after view loaded.
     */
    @FXML
    protected void initialize()
    {
        // Add CSS to top-level node
        mainAnchorPane.getStylesheets().add(mainApp.getStylesheet());
    }

    /**
     * Sets reference to main application.
     *
     * @param mainApp
     *         Main application
     */
    public void setMainApp(JFXMediaPlayer mainApp)
    {
        this.mainApp = mainApp;
    }

    /**
     * Sets a timer between an initial click to determine whether to interpret
     * mouse clicks as single or double clicks.
     */
    private void initMouseClickMeanings()
    {
        Stage stage = mainApp.getPrimaryStage();
        Scene scene = stage.getScene();

        // This value may need tuning:
        Duration maxTimeBetweenSequentialClicks = Duration.millis(250);

        PauseTransition clickTimer = new PauseTransition(maxTimeBetweenSequentialClicks);
        final IntegerProperty sequentialClickCount = new SimpleIntegerProperty(0);
        clickTimer.setOnFinished(event ->
        {
            int count = sequentialClickCount.get();
            if (count == 1) singleClickPauseAndPlay();
            if (count == 2) doubleClickToggleFullscreenMode(stage);
            sequentialClickCount.set(0);
        });

        scene.setOnMouseClicked(event ->
        {
            sequentialClickCount.set(sequentialClickCount.get() + 1);
            clickTimer.playFromStart();
        });
    }

    private void doubleClickToggleFullscreenMode(Stage stage)
    {
        logger.debug(stage.isFullScreen() ? "Setting stage to default dimensions with double-click."
                                          : "Setting stage to fullscreen with double-click.");
        stage.setFullScreen(!stage.isFullScreen());
    }

    /**
     * Pausing and playing media can be regulated by single clicks on the stage.
     */
    private void singleClickPauseAndPlay()
    {
        if (mediaPlayer != null)
        {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
            {
                int rate = (int) (mediaPlayer.getRate() * 10);

                logger.debug("Pausing video with single-click.");

                if (rate == 130)
                {
                    btnPlay.setGraphic(new ImageView("/images/1.3x-play-button.png"));
                }
                else
                {
                    btnPlay.setGraphic(new ImageView("/images/play-button.png"));
                }
                mediaPlayer.pause();

            }
            else
            {
                logger.debug("Playing video with single-click.");
                mediaPlayer.play();
                btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
            }
        }
    }

    /**
     * Initialize the stage to allow the mouse cursor to
     * move the application using dragging.
     */
    private void initMovablePlayer()
    {
        Stage stage = mainApp.getPrimaryStage();
        Scene scene = stage.getScene();
        // starting initial anchor point
        scene.setOnMousePressed(mouseEvent -> anchorPt = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY()));
        // dragging the entire stage
        scene.setOnMouseDragged(mouseEvent ->
        {
            if (anchorPt != null && previousLocation != null)
            {
                stage.setX(previousLocation.getX() + mouseEvent.getScreenX() - anchorPt.getX());
                stage.setY(previousLocation.getY() + mouseEvent.getScreenY() - anchorPt.getY());
            }
        });
        // set the current location
        scene.setOnMouseReleased(mouseEvent -> previousLocation = new Point2D(stage.getX(), stage.getY()));
        // Initialize previousLocation after Stage is shown
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, windowEvent ->
        {
            previousLocation = new Point2D(stage.getX(), stage.getY());
        });
    }

    /**
     * Initialize the Drag and Drop ability for media files.
     */
    private void initFileDragNDrop()
    {
        Stage stage = mainApp.getPrimaryStage();
        Scene scene = stage.getScene();
        scene.setOnDragOver(dragEvent ->
        {
            Dragboard db = dragEvent.getDragboard();
            if (db.hasFiles() || db.hasUrl())
            {
                dragEvent.acceptTransferModes(TransferMode.LINK);
            }
            else
            {
                dragEvent.consume();
            }
        });

        // Dropping over surface
        scene.setOnDragDropped(dragEvent ->
        {
            Dragboard db = dragEvent.getDragboard();
            boolean success = false;
            String filePath = null;
            if (db.hasFiles())
            {
                success = true;
                if (db.getFiles().size() > 0)
                {
                    try
                    {
                        filePath = db.getFiles().get(0).toURI().toURL().toString();
                        playMedia(filePath);
                        if (filePath.endsWith(".mp4"))
                        {
                            mediaPlayer.setOnError(()->
                                    System.out.println("media error: "+mediaPlayer.getError().toString()));
                            logger.info("Video file loaded.");
                        }
                        else if (filePath.endsWith(".mp3"))
                        {
                            logger.info("Audio file loaded.");
                        }
                        else
                        {
                            logger.info("Attempted to load unsupported file type.");
                        }
                    }
                    catch (MalformedURLException ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
            else
            {
                // audio file from some host or jar
                playMedia(db.getUrl());
                logger.info("File loaded from host or jar loaded.");
                success = true;
            }

            dragEvent.setDropCompleted(success);
            dragEvent.consume();
        });
    }

    /**
     * Sets up event handlers for play, pause and stop buttons.
     */
    private void setupButtonEventHandlers()
    {
        // STOP setOnMousePressed() EventHandler
        btnStop.setOnMousePressed(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING ||
                        mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
                {
                    mediaPlayer.stop();
                }
            }
        });

        btnStop.setOnMouseClicked(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                btnPlay.setGraphic(new ImageView("/images/play-button.png"));
                btnFfwd.setGraphic(new ImageView("/images/ffwd-button.png"));
                logger.debug("Media stopped.");
            }
        });

        // PLAY/PAUSE setOnMousePressed() EventHandler
        btnPlay.setOnMousePressed(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
                {
                    mediaPlayer.pause();
                }
                else
                {
                    mediaPlayer.play();
                    mediaPlayer.setRate(1.0);
                    mediaPlayer.setMute(false);
                }
            }
        });

        btnPlay.setOnMouseClicked(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
                {
                    logger.debug("Playing media at regular speed.");
                    btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
                }
                else
                {
                    logger.debug("Pausing media.");
                    btnPlay.setGraphic(new ImageView("/images/play-button.png"));
                    btnFfwd.setGraphic(new ImageView("/images/ffwd-button.png"));
                }
            }
        });

        btnSkipFwd.setOnMouseClicked(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                normalizePlayBack();
                normalizePlaybackButtons();

                logger.debug("Skipping forward.");
                setForwardSeekPointBasedOnCurrentLocation(mediaPlayer.getCurrentTime().toSeconds());


            }
        });

        btnSkipBkwd.setOnMouseClicked(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING ||
                        mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
                {
                    if (mediaPlayer.getCurrentTime().toSeconds() == 0.0)
                    {
                        btnPlay.setGraphic(new ImageView("/images/play-button.png"));
                        mediaPlayer.stop();
                        logger.debug("Already at start of media.");
                    }
                    else
                    {
                        logger.debug("Skipping backward.");
                        setBackwardSeekPointBasedOnCurrentLocation(mediaPlayer.getCurrentTime().toSeconds());
                    }
                }
                else if (mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED)
                {
                    btnPlay.setGraphic(new ImageView("/images/play-button.png"));
                }
            }
        });

        btnRewind.setOnMousePressed(mouseEvent ->
        {
            if (mediaPlayer != null)
            {
                mediaPlayer.seek(Duration.ZERO);
                logger.debug("Rewinding media.");
                mediaPlayer.play();
                logger.debug("Playing media from start.");
                btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
            }
        });

        btnFfwd.setOnMousePressed(mouseEvent ->
        {
            // Permits fast-forwarding from a paused state
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED)
            {
                logger.debug("Fast-forwarding from paused state.");
                mediaPlayer.setRate(1.3);
                mediaPlayer.setMute(false);
                mediaPlayer.play();
                btnFfwd.setGraphic(new ImageView("/images/1.3x-play-button.png"));
                btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
            }

            // Permits increasing rate on each mouse-click, returning to normal play after max is reached
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
            {
                int rate = (int) (mediaPlayer.getRate() * 10);
                switch (rate)
                {
                    case 80:
                        mediaPlayer.setRate(16.0);
                        mediaPlayer.setMute(true);
                        logger.debug("Playing media at 16x.");
                        btnFfwd.setGraphic(new ImageView("/images/16x-play-button.png"));
                        break;
                    case 40:
                        mediaPlayer.setRate(8.0);
                        mediaPlayer.setMute(true);
                        logger.debug("Playing media at 8x.");
                        btnFfwd.setGraphic(new ImageView("/images/8x-play-button.png"));
                        break;
                    case 20:
                        mediaPlayer.setRate(4.0);
                        mediaPlayer.setMute(true);
                        logger.debug("Playing media at 4x.");
                        btnFfwd.setGraphic(new ImageView("/images/4x-play-button.png"));
                        break;
                    case 13:
                        mediaPlayer.setRate(2.0);
                        mediaPlayer.setMute(true);
                        logger.debug("Playing media at 2x.");
                        btnFfwd.setGraphic(new ImageView("/images/2x-play-button.png"));
                        break;
                    case 10:
                        mediaPlayer.setRate(1.3);
                        mediaPlayer.setMute(false);
                        logger.debug("Playing media at 1.3x.");
                        btnFfwd.setGraphic(new ImageView("/images/1.3x-play-button.png"));
                        break;
                    case 160:
                        normalizePlayBack();
                        logger.debug("Playing media at regular speed.");
                        btnFfwd.setGraphic(new ImageView("/images/ffwd-button.png"));
                        break;
                }
            }
        });
    }

    private void setBackwardSeekPointBasedOnCurrentLocation(double currentLocation)
    {
        if (currentLocation > seekPointsList.get(2))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(2)));
            btnPlay.setGraphic(new ImageView("/images/play-button.png"));
            mediaPlayer.pause();
        }
        else if (currentLocation > seekPointsList.get(1))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(1)));
            btnPlay.setGraphic(new ImageView("/images/play-button.png"));
            mediaPlayer.pause();
        }
        else if (currentLocation > seekPointsList.get(0))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(0)));
            btnPlay.setGraphic(new ImageView("/images/play-button.png"));
            mediaPlayer.pause();
        }
        else
        {
            mediaPlayer.seek(Duration.ZERO);
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING)
            {
                btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
            }
            else
            {
                btnPlay.setGraphic(new ImageView("/images/play-button.png"));
            }
            logger.debug("Start of media.");
        }

    }

    private void setForwardSeekPointBasedOnCurrentLocation(double currentLocation)
    {
        if (mediaPlayer.getCurrentRate() > 1.0)
        {
            normalizePlayBack();
            normalizePlaybackButtons();
        }

        if (currentLocation < seekPointsList.get(0))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(0)));
            logger.debug("Skipping to first seek point.");
        }
        else if (currentLocation < seekPointsList.get(1))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(1)));
            logger.debug("Skipping to second seek point.");
        }
        else if (currentLocation < seekPointsList.get(2))
        {
            mediaPlayer.seek(Duration.seconds(seekPointsList.get(2)));
            logger.debug("Skipping to third seek point.");
        }
        else
        {
            //mediaPlayer.stop();
            logger.debug("End of media reached, setting back to start.");
            mediaPlayer.seek(Duration.ZERO);
        }
        mediaPlayer.play();
    }

    private void normalizePlayBack()
    {
        mediaPlayer.setRate(1.0);
        mediaPlayer.setMute(false);
    }

    private void normalizePlaybackButtons()
    {
        btnPlay.setGraphic(new ImageView("/images/pause-button.png"));
        btnFfwd.setGraphic(new ImageView("/images/ffwd-button.png"));
    }

    /**
     * After a file is dragged onto the application a new MediaPlayer
     * instance is created with a media file.
     *
     * @param url
     *         The URL pointing to an audio file
     */
    private void playMedia(String url)
    {
        // Initialize media player
        if (mediaPlayer != null)
        {
            mediaPlayer.pause();
            mediaPlayer.setOnPaused(null);
            mediaPlayer.setOnPlaying(null);
            mediaPlayer.setOnReady(null);
            mediaPlayer.currentTimeProperty().removeListener(progressListener);
            mediaPlayer.setAudioSpectrumListener(null);
        }

        // Set the media to the URL of the item dragged onto the player
        Media media = new Media(url);

        // Create a new media player
        mediaPlayer = new MediaPlayer(media);

        // Set the mediaPlayer to display video
        mvMediaView.setMediaPlayer(mediaPlayer);

        // Listener moves the slider for progress as the media is playing
        mediaPlayer.currentTimeProperty().addListener(progressListener);

        // When media is ready, set slider to span the duration of it
        mediaPlayer.setOnReady(() ->
        {
            sldSlider.setValue(0);
            double mediaDurationInSeconds = mediaPlayer.getMedia().getDuration().toSeconds();
            sldSlider.setMax(mediaDurationInSeconds);

            // Bind elapsed / remaining time label
            lblTimeElapsedAndRemaining.textProperty().bind(createBindingTimeElapsedAndRemaining());

            // Create seek points for quicker segmented navigation of media
            createSeekPoints(mediaDurationInSeconds);

            DisplayMetadata();

            //Set appropriate icon (pause instead of play)
            btnPlay.setGraphic(new ImageView("/images/pause-button.png"));

            mediaPlayer.play();
            logger.debug("Playing media at regular speed.");
        });


        // Set media back to the beginning when done
        mediaPlayer.setOnEndOfMedia(() ->
        {
            mediaPlayer.stop();
            logger.info("End of media reached.");
            btnPlay.setGraphic(new ImageView("/images/play-button.png"));

        });

    }

    // Add seek points to media
    private void createSeekPoints(double mediaDurationInSeconds)
    {
        seekPointsList = new ArrayList<Double>();

        for (int i = 1; i < numSeekPoints + 1; i++)
        {
            seekPointsList.add(mediaDurationInSeconds / 4 * i);
        }

    }

    @SuppressWarnings("unused")
    private void SetMarkers(Media media)
    {
        ObservableMap<String, Duration> markers = media.getMarkers();
        markers.put("START", Duration.ZERO);
        markers.put("INTERVAL", media.getDuration().divide(2.0));
        markers.put("END", media.getDuration());
    }

    private void DisplayMetadata()
    {
        // display media's metadata
        ObservableMap<String, Object> metadata = mediaPlayer.getMedia().getMetadata();
        if (metadata != null)
        {
            for (String key : metadata.keySet())
            {
                logger.info(key + " = " + metadata.get(key));
            }
        }
        else
        {
            logger.info("Failed to display metadata.");
        }
    }

    /**
     * Configure MediaView node.
     */
    private void configMediaView()
    {
        Stage stage = mainApp.getPrimaryStage();

        mvMediaView.setPreserveRatio(true);

        mvMediaView.setSmooth(true);
        mvMediaView.setX(2);
        mvMediaView.setY(2);
        mvMediaView.fitWidthProperty().bind(stage.getScene().widthProperty().subtract(4));
        mvMediaView.fitHeightProperty().bind(stage.getScene().heightProperty().subtract(4));
        // Attempt to round corners to match containing border.
        mvMediaView.getStyleClass().add("media-fxml");
        // sometimes loading errors occur
        mvMediaView.setOnError(mediaErrorEvent ->
        {
            mediaErrorEvent.getMediaError().printStackTrace();
        });
    }

    /**
     * A position slider to seek backward and forward
     * that is bound to a media player control.
     */
    private void configSlider()
    {
        sldSlider.valueProperty().addListener((observable) ->
        {
            if (sldSlider.isValueChanging())
            {
                if (mediaPlayer != null)
                {
                    // convert seconds to millis
                    double dur = sldSlider.getValue() * 1000;
                    mediaPlayer.seek(Duration.millis(dur));
                    logger.debug("Slider configured.");
                }
            }
        });

        // Added mouse click event for slider.
        sldSlider.setOnMouseClicked(event ->
        {
            Duration duration = Duration.seconds(sldSlider.getValue());
            mediaPlayer.seek(duration);
            logger.info(String.format("Slider clicked at %s.", convertSecondsToHhMmSs(duration.toSeconds())));
        });
    }

    /**
     * @return single StringBinding for time elapsed and time remaining for current media being played.
     */
    private StringBinding createBindingTimeElapsedAndRemaining()
    {
        return Bindings.createStringBinding(() ->
                        String.format("%s / %s", convertSecondsToHhMmSs(elapsedSecondsProperty.longValue()),
                                convertSecondsToHhMmSs(
                                        mediaPlayer.getMedia().getDuration().toSeconds() - elapsedSecondsProperty.longValue())),
                elapsedSecondsProperty);
    }

}

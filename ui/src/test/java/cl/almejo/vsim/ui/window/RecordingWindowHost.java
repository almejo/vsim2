package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.JMenuBar;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** What a frame and the application would do for a {@link WindowTabs}, recorded (a frame cannot be built headless). */
final class RecordingWindowHost implements WindowTabs.Host {
    JMenuBar menuBar;
    boolean lastHolder = true;
    boolean answerToSave = true;
    final List<OpenDocument> saveQuestions = new ArrayList<>();
    final List<OpenDocument> closedGroups = new ArrayList<>();
    final List<OpenDocument> closingGroups = new ArrayList<>();
    final List<OpenDocument> changed = new ArrayList<>();
    final List<OpenDocument> droppedOutside = new ArrayList<>();
    PlaceMarks placeMarks = PlaceMarks.NONE;
    int emptied;
    int activeChanges;

    @Override
    public void showMenuBar(JMenuBar shown) {
        menuBar = shown;
    }

    @Override
    public boolean isMaximized() {
        return false;
    }

    @Override
    public boolean isLastHolder(OpenDocument document) {
        return lastHolder;
    }

    @Override
    public boolean confirmSave(OpenDocument document) {
        saveQuestions.add(document);
        return answerToSave;
    }

    @Override
    public void tabsChanged(OpenDocument document) {
        changed.add(document);
    }

    @Override
    public void groupClosing(OpenDocument document) {
        closingGroups.add(document);
    }

    @Override
    public void groupClosed(OpenDocument document) {
        closedGroups.add(document);
    }

    @Override
    public void emptied() {
        emptied++;
    }

    @Override
    public void activeTabChanged() {
        activeChanges++;
    }

    @Override
    public int viewNumber(OpenDocument document, int mainTabIndex) {
        return 0;
    }

    @Override
    public void droppedOutside(OpenDocument document, Point screenPoint) {
        droppedOutside.add(document);
    }

    @Override
    public List<WindowMenu.Entry> everyTab() {
        return List.of();
    }

    @Override
    public PlaceMarks placeMarks() {
        return placeMarks;
    }
}

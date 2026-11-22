package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.closed.ClosedEntry;

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
    int newTabs;
    Runnable onNewTab = () -> {
    };
    int emptied;
    int activeChanges;
    final List<ClosedEntry> closedByUser = new ArrayList<>();
    java.util.function.Consumer<ClosedEntry> onClosedByUser = closed -> {
    };
    int windowsClosedByUser;
    List<cl.almejo.vsim.ui.window.closed.ClosedGroup> closedWindowGroups = List.of();
    int closedWindowActiveGroup = -1;
    java.util.function.Consumer<OpenDocument> onSaveQuestion = document -> {
    };
    Runnable onWindowClosedByUser = () -> {
    };
    java.util.function.Consumer<OpenDocument> onGroupClosed = document -> {
    };

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
        onSaveQuestion.accept(document);
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
        onGroupClosed.accept(document);
    }

    @Override
    public void closedByUser(ClosedEntry closed) {
        closedByUser.add(closed);
        onClosedByUser.accept(closed);
    }

    @Override
    public void windowClosedByUser(List<cl.almejo.vsim.ui.window.closed.ClosedGroup> groups, int activeGroup) {
        windowsClosedByUser++;
        closedWindowGroups = groups;
        closedWindowActiveGroup = activeGroup;
        onWindowClosedByUser.run();
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

    @Override
    public void newTab() {
        newTabs++;
        onNewTab.run();
    }
}

package cl.almejo.vsim.ui.panels;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.util.function.BiFunction;

/**
 * List and combo box renderers that only change the text a row shows: the default look (colours, selection, font)
 * stays, the text comes from the value and from the row's index ({@code -1} is the combo box's closed box).
 */
public final class TextRenderers {

    private TextRenderers() {
    }

    /** A renderer whose text is {@code text} of every value and its index. */
    public static ListCellRenderer<Object> of(BiFunction<Object, Integer, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(text.apply(value, index));
                return this;
            }
        };
    }

    /** Like {@link #of(BiFunction)}, but the text of a value that is not a {@code type} stays the default one. */
    public static <T> ListCellRenderer<Object> of(Class<T> type, BiFunction<? super T, Integer, String> text) {
        return of((value, index) -> type.isInstance(value) ? text.apply(type.cast(value), index)
                : defaultText(value));
    }

    private static String defaultText(Object value) {
        return value == null ? "" : value.toString();
    }
}

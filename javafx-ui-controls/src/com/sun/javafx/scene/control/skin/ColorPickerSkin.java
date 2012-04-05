/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.javafx.scene.control.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import javafx.scene.Node;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.RectangleBuilder;
import com.sun.javafx.scene.control.behavior.ColorPickerBehavior;
import com.sun.javafx.scene.control.ColorPicker;
import com.sun.javafx.scene.control.ColorPickerPanel;

/**
 *
 * @author paru
 */
public class ColorPickerSkin<T> extends ComboBoxPopupControl<T> {

    private Label displayNode; 
    private StackPane icon; 
    private Rectangle colorRect; 
    private ColorPickerPanel popup = new ColorPickerPanel(Color.WHITE);
    
    private boolean behaveLikeSplitButton = false;
    
    private ObjectProperty<Color> color = new SimpleObjectProperty<Color>(Color.WHITE);
    public ObjectProperty<Color> colorProperty() { return color; }
    public Color getColor() { return color.get(); }
    public void setColor(Color newColor) { color.set(newColor); }
    
    public ColorPickerSkin(final ColorPicker<T> colorPicker) {
        super(colorPicker, new ColorPickerBehavior<T>(colorPicker));
        initialize();
        if (arrowButton.getOnMouseReleased() == null) {
            colorPicker.setOnMouseReleased(new EventHandler<MouseEvent>() {
                @Override public void handle(MouseEvent e) {
                    ((ColorPickerBehavior)getBehavior()).mouseReleased(e, behaveLikeSplitButton);
                    e.consume();
                }
            });
        }
    }
    
    private void initialize() {
        updateComboBoxMode();
        popup.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent t) {
                ((ColorPicker)getSkinnable()).hide();
            }
        });
        popup.colorProperty().addListener(new ChangeListener<Color>() {
            @Override public void changed(ObservableValue<? extends Color> ov, Color t, Color t1) {
                setColor(t1);
            }
        });
    }
     
    private void updateComboBoxMode() {
        if (getSkinnable().getStyleClass().contains(ColorPicker.STYLE_CLASS_BUTTON)) {
            setMode(ComboBoxMode.BUTTON);
            behaveLikeSplitButton = false;
        }
        else if (getSkinnable().getStyleClass().contains(ColorPicker.STYLE_CLASS_SPLIT_BUTTON)) {
            setMode(ComboBoxMode.SPLITBUTTON);
            behaveLikeSplitButton = true;
        }
    }
    
      private static final List<String> colorNames = new ArrayList<String>();
    static {
        // Initializes the namedColors map
        Color.web("white", 1.0);
        for (Field f : Color.class.getDeclaredFields()) {
            int modifier = f.getModifiers();
            if (Modifier.isStatic(modifier) && Modifier.isPublic(modifier) && f.getType().equals(Color.class)) {
                colorNames.add(f.getName());
            }
        }
        Collections.sort(colorNames);
    }
    
    private static String colorValueToWeb(Color c) {
        String web = null;
        if (colorNames != null) {
            // Find a name for the color. Note that there can
            // be more than one name for a color, e.g. #ff0ff
            // is named both "fuchsia" and "magenta".
            // We return the first name encountered (alphabetically).

            // TODO: Use a hash map for performance
            for (String name : colorNames) {
                if (Color.web(name).equals(c)) {
                    web = name;
                    break;
                }
            }
        }
        if (web == null) {
            web = String.format((Locale) null, "%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
        }
        return web;
    }
    
    @Override protected Node getPopupContent() {
       return popup;
    }
    
    @Override public Node getDisplayNode() {
        if (displayNode == null) {
            displayNode = new Label();
            displayNode.getStyleClass().add("color-picker-label");
            // label text
            displayNode.textProperty().bind(new StringBinding() {
                { bind(color); }
                @Override protected String computeValue() {
                    return colorValueToWeb(getColor());
                }
            });
            // label graphic
            icon = new StackPane();
            icon.getStyleClass().add("picker-color");
            colorRect = RectangleBuilder.create()
                    .width(16).height(16)
                    .build();
            colorRect.fillProperty().bind(new ObjectBinding<Paint>() {
                { bind(color); }
                @Override protected Paint computeValue() {
                    return getColor();
                }
            });         
            
            icon.getChildren().add(colorRect);
            displayNode.setGraphic(icon);
            if (displayNode.getOnMouseReleased() == null) {
                displayNode.setOnMouseReleased(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        ((ColorPickerBehavior)getBehavior()).mouseReleased(e, false);
                        e.consume();
                    }
                });
            }
        }
        return displayNode;
    }
    
    @Override protected void layoutChildren() {
        updateComboBoxMode();
        super.layoutChildren();
    }
    
}
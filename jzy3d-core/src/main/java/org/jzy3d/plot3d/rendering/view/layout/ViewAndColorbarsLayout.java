package org.jzy3d.plot3d.rendering.view.layout;

import java.util.ArrayList;
// import java.awt.Rectangle;
import java.util.List;
import org.jzy3d.chart.Chart;
import org.jzy3d.painters.IPainter;
import org.jzy3d.plot3d.primitives.Drawable;
import org.jzy3d.plot3d.rendering.canvas.ICanvas;
import org.jzy3d.plot3d.rendering.legends.ILegend;
import org.jzy3d.plot3d.rendering.view.AbstractViewportManager;
import org.jzy3d.plot3d.rendering.view.Camera;
import org.jzy3d.plot3d.rendering.view.IViewOverlay;
import org.jzy3d.plot3d.rendering.view.View;
import org.jzy3d.plot3d.rendering.view.ViewportBuilder;
import org.jzy3d.plot3d.rendering.view.ViewportConfiguration;
import org.jzy3d.plot3d.rendering.view.ViewportMode;

/**
 * This class handles the layout of a main 3D plot on the left with additional legends (colorbars)
 * on the right side.
 * 
 * The canvas is composed of two {@link AbstractViewportManager}
 * <ul>
 * <li>The {@link View} which handles its viewport with the {@link Camera}. If the view is and
 * {@link AWTView} or children, it also allows defining 2D {@link IViewOverlay} that can span on the
 * left 3D side only or on the full canvas (hence also covering the 2D right side).
 * <li>The {@link ILegend} objects which handle their viewport on their own. They are added to the
 * right of the chart according to the number of {@link Drawable} having a {@link ILegend} set such
 * as {@link AWTColorbarLegend}
 * </ul>
 * 
 * This allow making a composition of 3D and 2D content in a single screen.
 * 
 * @author Martin Pernollet
 */
public class ViewAndColorbarsLayout implements IViewportLayout {
  protected float screenSeparator = 1.0f;
  protected boolean hasColorbars = true;

  protected ViewportConfiguration sceneViewport;
  protected ViewportConfiguration backgroundViewport;

  // stored at render time for later layout processing
  protected float legendsWidth = 0;


  /**
   * This shrink colorbar is actually not supported by this implementation but made available and
   * used by classes that inherit this class
   */
  protected boolean shrinkColorbar = false;
  protected int colorbarRightMargin = 10;
  protected Chart chart;

  @Override
  public void update(Chart chart) {
    this.chart = chart;
    final ICanvas canvas = chart.getCanvas();
    final List<ILegend> list = getLegends(chart);

    computeSeparator(canvas, list);
    sceneViewport = ViewportBuilder.column(canvas, 0, screenSeparator);
    backgroundViewport = new ViewportConfiguration(canvas);
  }

  // Separator only used for native since emulgl can not have two viewport side by side,
  // only a single viewport with images rendered on top
  protected void computeSeparator(final ICanvas canvas, final List<ILegend> list) {
    hasColorbars = list.size() > 0;
    
    if (hasColorbars) {
      
      int minWidth = 0;
      
      for (ILegend data : list) {
        minWidth += data.getMinimumDimension().width;
      }
      minWidth *= chart.getView().getPixelScale().x;

      screenSeparator = computeSeparator(canvas, minWidth);
    
    
    } else {
      screenSeparator = 1.0f;
    }
  }

  protected float computeSeparator(final ICanvas canvas, int minWidth) {
    return ((float) (canvas.getRendererWidth() - minWidth)) / ((float) canvas.getRendererWidth());
  }

  /**
   * Once rendered, this layout knows the colorbar width which can be retrieved with {@link #getLegendsWidth()}
   */
  @Override
  public void render(IPainter painter, Chart chart) {
    View view = chart.getView();

    // System.out.println("ViewAndColorbarLayout w:" + chart.getCanvas().getRendererWidth() + " h:"
    // + chart.getCanvas().getRendererHeight());

    // Background
    view.renderBackground(backgroundViewport);

    // Underlay
    // if(view.getCamera().getLastViewPort()!=null)
    // view.renderOverlay(view.getCamera().getLastViewPort());

    // Scene
    view.renderScene(sceneViewport);

    // Legend
    renderLegends(painter, chart);

    // Overlay
    if (view.getCamera().getLastViewPort() != null) {
      view.renderOverlay(view.getCamera().getLastViewPort());
    } else {
      view.renderOverlay(); // ignore colorbar
    }
  }

  protected void renderLegends(IPainter painter, Chart chart) {
    if (hasColorbars) {
      updateLegendsWidth(chart);
      
      renderLegends(painter, screenSeparator, 1.0f, getLegends(chart), chart.getCanvas());
    } else {
      legendsWidth = 0;
    }
  }

  protected void updateLegendsWidth(Chart chart) {
    legendsWidth = (1-screenSeparator) * chart.getCanvas().getRendererWidth();
    //legendsWidth /= chart.getView().getPixelScale().x;
  }

  /**
   * Renders the legends within the screen slice given by the left and right parameters.
   */
  protected void renderLegends(IPainter painter, float left, float right, List<ILegend> legends,
      ICanvas canvas) {
    float slice = (right - left) / legends.size();
    int k = 0;
    for (ILegend legend : legends) {

      int width = canvas.getRendererWidth();
      int height = canvas.getRendererHeight();
      float theLeft = left + slice * (k++);
      float theRight = left + slice * k;
      
      //System.out.println("ViewAndColorbars : width:" + width + " height:" + height);
      //System.out.println("ViewAndColorbars : TheLeft:" + theLeft + " TheRight:" + theRight);

      legend.setFont(painter.getView().getAxis().getLayout().getFont());
      legend.setViewportMode(ViewportMode.STRETCH_TO_FILL);
      legend.setViewPort(width, height, theLeft, theRight);
      legend.render(painter);
    }
  }

  protected List<ILegend> getLegends(Chart chart) {
    if (chart != null && chart.getScene() != null && chart.getScene().getGraph() != null
        && chart.getScene().getGraph().getLegends() != null)
      return chart.getScene().getGraph().getLegends();
    else
      return new ArrayList<>();
  }

  /**
   * Return the scene viewport as it was processed according to the number of legends to display.
   */
  public ViewportConfiguration getSceneViewport() {
    return sceneViewport;
  }

  /** Return the scene viewport as it was processed to cover the whole canvas. */
  public ViewportConfiguration getBackgroundViewport() {
    return backgroundViewport;
  }

  public boolean isShrinkColorbar() {
    return shrinkColorbar;
  }

  /**
   * If true, will let the colorbar be as thin as possible and stick to the right of the chart.
   * 
   * If the input value is different than internal state, then the chart will be updated to ensure
   * the setting takes effect immediately.
   */
  public void setShrinkColorbar(boolean shrinkColorbar) {
    boolean updateDisplay = (shrinkColorbar != this.shrinkColorbar);

    this.shrinkColorbar = shrinkColorbar;

    if (updateDisplay && chart != null) {
      chart.render();
    }
  }

  public int getColorbarRightMargin() {
    return colorbarRightMargin;
  }

  /**
   * Set a right margin for colorbar.
   * 
   * If the input value is different than internal state, then the chart will be updated to ensure
   * the setting takes effect immediately.
   */
  public void setColorbarRightMargin(int colorbarRightMargin) {
    boolean updateDisplay = (colorbarRightMargin != this.colorbarRightMargin);

    this.colorbarRightMargin = colorbarRightMargin;

    if (updateDisplay) {
      chart.render();
    }
  }

  /**
   * Return the legend width as it was processed at the rendering stage. Hence this value is defined
   * after a first rendering. It is used for processing the remaining part of the layout (other
   * viewport needing the colorbar width information).
   */
  public float getLegendsWidth() {
    return legendsWidth;
  }
}

/*
 * BackgroundDecoder.java
 * Copyright (c) 2005-2020 Radek Burget
 *
 * CSSBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CSSBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with CSSBox. If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 11. 4. 2020, 12:20:05 by burgetr
 */
package org.fit.cssbox.css;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.CSSDecoder;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.Rectangle;
import org.fit.cssbox.layout.VisualContext;
import org.fit.cssbox.render.BackgroundImageGradient;
import org.fit.cssbox.render.BackgroundImageImage;
import org.fit.cssbox.render.GradientStop;
import org.fit.cssbox.render.LinearGradient;
import org.fit.net.DataURLHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFunction;
import cz.vutbr.web.css.TermLengthOrPercent;
import cz.vutbr.web.css.TermList;
import cz.vutbr.web.css.TermURI;
import cz.vutbr.web.css.CSSProperty.BackgroundAttachment;
import cz.vutbr.web.css.CSSProperty.BackgroundOrigin;
import cz.vutbr.web.css.CSSProperty.BackgroundRepeat;
import cz.vutbr.web.css.CSSProperty.BackgroundSize;
import cz.vutbr.web.csskit.Color;

/**
 * A decoder of background color and image declarations for a given element box. It loads the
 * element style and provides the color and a representation of the images used in its
 * background. 
 * 
 * @author burgetr
 */
public class BackgroundDecoder
{
    private static Logger log = LoggerFactory.getLogger(BackgroundDecoder.class);
    
    /** The owning element box */
    private ElementBox owner;
    private VisualContext ctx;
    private NodeData style;
    
    /** Background color or null when transparent */
    private Color bgcolor;
    
    /** Background images or null when there are no background images */
    private List<BackgroundImage> bgimages;
    
    
    /**
     * Creates a background decoder for a given element box.
     * 
     * @param owner The element box that owns the background.
     */
    public BackgroundDecoder(ElementBox owner)
    {
        this.owner = owner;
        ctx = owner.getVisualContext();
        style = owner.getStyle();
        loadBackground();
    }

    /**
     * Returns the element box that owns the background.
     * @return the owner element box
     */
    public ElementBox getOwner()
    {
        return owner;
    }

    /**
     * Obtains the efficient background color to be used for drawing the background.
     * @return the background color or null when transparent
     */
    public Color getBgcolor()
    {
        return bgcolor;
    }

    /**
     * Obtains the list of background images of the element.
     * @return a list of the background images
     */
    public List<BackgroundImage> getBackgroundImages()
    {
        return bgimages;
    }

    /**
     * Checks whether there is no background defined for the owner element.
     * @return {@code true} when the background is empty.
     */
    public boolean isBackgroundEmpty()
    {
        return (bgcolor == null) && (bgimages == null);
    }
    
    /**
     * Loads the background information from the style
     */
    protected void loadBackground()
    {
        if (style != null)
        {
            CSSProperty.BackgroundColor bg = style.getProperty("background-color");
            if (bg == CSSProperty.BackgroundColor.color)
            {
                TermColor bgc = style.getSpecifiedValue(TermColor.class, "background-color");
                if (bgc.isTransparent())
                    bgcolor = null;
                else
                    bgcolor = bgc.getValue();
            }
            else
                bgcolor = null;
    
            bgimages = loadBackgroundImages();
        }
    }
    
    /**
     * Loads background images from the owner element style. Considers their positions, repetition, etc.
     * @return a list of images
     */
    protected List<BackgroundImage> loadBackgroundImages()
    {
        final int count = style.getListSize("background-image", true);
        if (count > 0)
        {
            List<BackgroundImage> bgimages = new ArrayList<BackgroundImage>(count);
            for (int i = 0; i < count; i++)
            {
                final BackgroundImage bgimg = loadBackgroundImage(style, i);
                if (bgimg != null)
                    bgimages.add(bgimg);
            }
            return bgimages.isEmpty() ? null : bgimages; //return null when there are no images
        }
        else
            return null;
    }
    
    /**
     * Creates background a image structure based on a style definition.
     * @param style the style definition
     * @param index index of the image to take (there may be multiple images defined, 0 for the first image defined)
     * @return The corresponding BackgroundImage object.
     */
    protected BackgroundImage loadBackgroundImage(NodeData style, int index)
    {
        CSSProperty.BackgroundImage image = style.getProperty("background-image", index);
        if (image == CSSProperty.BackgroundImage.uri || image == CSSProperty.BackgroundImage.gradient)
        {
            try {
                BackgroundImage ret = null;
                //position
                CSSProperty.BackgroundPosition position = style.getProperty("background-position", index);
                TermList positionValues = style.getValue(TermList.class, "background-position", index);
                //repeat
                CSSProperty.BackgroundRepeat repeat = style.getProperty("background-repeat", index);
                if (repeat == null) repeat = BackgroundRepeat.REPEAT;
                //origin
                CSSProperty.BackgroundOrigin origin = style.getProperty("background-origin", index);
                if (origin == null) origin = BackgroundOrigin.PADDING_BOX;
                //attachment
                CSSProperty.BackgroundAttachment attachment = style.getProperty("background-attachment", index);
                if (attachment == null) attachment = BackgroundAttachment.SCROLL;
                //size
                CSSProperty.BackgroundSize size = style.getProperty("background-size", index);
                TermList sizeValues = null;
                if (size == null) size = BackgroundSize.list_values;
                else if (size == BackgroundSize.list_values)
                    sizeValues = style.getValue(TermList.class, "background-size", index);
                //image
                if (image == CSSProperty.BackgroundImage.uri)
                {
                    TermURI urlstring = style.getValue(TermURI.class, "background-image", index);
                    URL url = DataURLHandler.createURL(urlstring.getBase(), urlstring.getValue());
                    BackgroundImageImage bgimg =
                            new BackgroundImageImage(owner, url, position, positionValues, repeat, 
                                    attachment, origin, size, sizeValues);
                    if (ctx.getConfig().getLoadBackgroundImages())
                        bgimg.setImage(ctx.getImageLoader().loadImage(url));
                    ret = bgimg;
                }
                else if (image == CSSProperty.BackgroundImage.gradient)
                {
                    BackgroundImageGradient bgimg =
                            new BackgroundImageGradient(owner, position, positionValues, repeat, 
                                    attachment, origin, size, sizeValues);
                    Rectangle bgsize = bgimg.getComputedPosition();
                    TermFunction.Gradient values = style.getValue(TermFunction.Gradient.class, "background-image", index);
                    if (values instanceof TermFunction.LinearGradient)
                    {
                        TermFunction.LinearGradient spec = (TermFunction.LinearGradient) values;
                        LinearGradient grad = new LinearGradient();
                        double angle = (spec.getAngle() != null) ? ctx.degAngle(spec.getAngle()) : 180.0;
                        grad.setAngleDeg(angle, bgsize.width, bgsize.height);
                        for (TermFunction.Gradient.ColorStop stop : spec.getColorStops())
                        {
                            Color color = stop.getColor().getValue();
                            Float percentage = decodePercentage(stop.getLength(), new CSSDecoder(ctx), grad.getLength());
                            grad.addStop(new GradientStop(color, percentage));
                        }
                        grad.recomputeStops();
                        bgimg.setGradient(grad);
                        ret = bgimg;
                    }
                    //TODO other gradients
                }
                return ret;
            } catch (MalformedURLException e) {
                log.warn(e.getMessage());
                return null;
            }
        }
        else
            return null;
    }
    
    private Float decodePercentage(TermLengthOrPercent spec, CSSDecoder dec, double wholeLength)
    {
        if (spec != null)
        {
            if (spec.isPercentage())
            {
                return spec.getValue();
            }
            else
            {
                float abs = dec.getLength(spec, false, 0, 0, 0);
                return (float) ((abs / wholeLength) * 100.0);
            }
        }
        else
            return null;
    }
    
}

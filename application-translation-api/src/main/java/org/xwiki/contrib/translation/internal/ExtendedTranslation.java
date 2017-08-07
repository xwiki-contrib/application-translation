/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.translation.internal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.xwiki.context.ExecutionContext;
import org.xwiki.localization.Translation;
import org.xwiki.localization.TranslationBundle;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

/**
 * Performs special rendering of translation keys, see
 * {@link #ExtendedTranslation(Translation, Map, ExecutionContext, BlockRenderer)} for more details.
 *
 * @version $Id$
 */
public class ExtendedTranslation implements Translation
{
    private static final String CONTEXT_TRANSLATION_KEY = "translationApplication";

    private Translation wrappedTranslation;

    private ExecutionContext executionContext;

    private Map<String, Boolean> options;

    private BlockRenderer plainTextRenderer;

    /**
     * @param wrappedTranslation the original Translation object that we're wrapping
     * @param options the action that needs to be implemented ("showKeys": return translation keys instead of
     *                translations, "saveKeys": save keys/translations in the Execution Context,
     *                "showHints": display translations with some visually visible colors + display the keys as
     *                hovering hints (not working yet)
     * @param executionContext the context in which to store the keys/translations when "saveKeys" option is on
     * @param plainTextRenderer the renderer to use to generate the translation text that we save for the "saveKeys"
     *                          and "showHints" options
     */
    public ExtendedTranslation(Translation wrappedTranslation, Map<String, Boolean> options,
        ExecutionContext executionContext, BlockRenderer plainTextRenderer)
    {
        this.wrappedTranslation = wrappedTranslation;
        this.options = options;
        this.executionContext = executionContext;
        this.plainTextRenderer = plainTextRenderer;
    }

    @Override
    public TranslationBundle getBundle()
    {
        return this.wrappedTranslation.getBundle();
    }

    @Override
    public Locale getLocale()
    {
        return this.wrappedTranslation.getLocale();
    }

    @Override
    public String getKey()
    {
        return this.wrappedTranslation.getKey();
    }

    @Override
    public Object getRawSource()
    {
        return this.wrappedTranslation.getRawSource();
    }

    @Override
    public Block render(Locale locale, Object... parameters)
    {
        return generateBlock(locale, parameters);
    }

    @Override
    public Block render(Object... parameters)
    {
        return generateBlock(null, parameters);
    }

    private Block generateBlock(Locale locale, Object... parameters)
    {
        Block result = handleSaveKeys(locale, parameters);
        if (result != null) {
            return result;
        }

        result = handleShowKeys(locale, parameters);
        if (result != null) {
            return result;
        }

        result = handleShowHints(locale, parameters);
        if (result != null) {
            return result;
        }

        return this.wrappedTranslation.render(locale, parameters);
    }

    private Block handleShowKeys(Locale locale, Object... parameters)
    {
        // Show the translations keys in the UI instead of the translations
        if (this.options.get(ExtendedLocalizationManager.SHOWKEYS)) {
            if (getKey() == null) {
                return new CompositeBlock();
            } else {
                return new WordBlock(getKey());
            }
        }

        return null;
    }

    private Block handleSaveKeys(Locale locale, Object... parameters)
    {
        // Save the translation key + rendered blocks in the Execution Context for later display in the UI.
        if (this.options.get(ExtendedLocalizationManager.SAVEKEYS)) {
            Block result = this.wrappedTranslation.render(locale, parameters);
            if (this.executionContext != null) {
                Map<String, String> data =
                    (Map<String, String>) this.executionContext.getProperty(CONTEXT_TRANSLATION_KEY);
                if (data == null) {
                    data = new HashMap<>();
                    this.executionContext.setProperty(CONTEXT_TRANSLATION_KEY, data);
                }
                if (getKey() != null) {
                    data.put(getKey(), renderBlocks(result));
                }
            }
            return result;
        }

        return null;
    }

    private Block handleShowHints(Locale locale, Object... parameters)
    {
        // Show the translations keys as hints when hovering over the text and show the translations in a different
        // style. We use the syntax: %%...translation here%%...key here...%% and then use CSS to present it
        // Note: We transform the translated content into plain text and thus remove any styling in order to normalize
        // the translation to be able to present them nicely in CSS.
        //
        // FTR here's an example of JS to convert the generated translation syntax into something nicer:
        //require(['jquery'], function($) {
        //  $(window).on('load', function () {
        //    $('body :not(script)').contents().filter(function() {
        //      return this.nodeType === 3;
        //    }).replaceWith(function() {
        //      return this.nodeValue.replace(/\%\%(.*)\%\%(.*)\%\%/,"<span class='translation' alt=$2>$1</span>");
        //    });
        //  });
        //});
        //
        // TODO: This approach is not working that well because $services.translation.render() is used in VM files
        // and thus the generated text is evaluated as HTML and this breaks the JS above. Thus we would need to
        // use a different approach. A better solution is to override LocalizationScriptService and to generate proper
        // HTML or Syntax depending on whether we're rendering a page or in a VM (see http://bit.ly/2vbylmL).
        if (this.options.get(ExtendedLocalizationManager.SHOWHINTS)) {
            if (getKey() == null) {
                return new CompositeBlock();
            } else {
                Block result = this.wrappedTranslation.render(locale, parameters);
                return new VerbatimBlock(String.format("%%%%%s%%%%%s%%%%", renderBlocks(result), getKey()), true);
            }
        }

        return null;
    }

    private String renderBlocks(Block block)
    {
        try {
            WikiPrinter printer = new DefaultWikiPrinter();
            this.plainTextRenderer.render(block, printer);
            return printer.toString();
        } catch (Exception e) {
            return String.format("ERROR: [%s]", ExceptionUtils.getRootCauseMessage(e));
        }
    }
}

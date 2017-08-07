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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.localization.Translation;
import org.xwiki.localization.internal.DefaultLocalizationManager;
import org.xwiki.rendering.renderer.BlockRenderer;

/**
 * Override {@link DefaultLocalizationManager#getTranslation(String, Locale)} to inject our own implementation of
 * {@link Translation} so that we can control the rendering of translations, see
 * {@link ExtendedTranslation#ExtendedTranslation(Translation, Map, ExecutionContext, BlockRenderer)} for more details.
 *
 * @version $Id$
 */
@Component
@Singleton
public class ExtendedLocalizationManager extends DefaultLocalizationManager
{
    static final String SHOWKEYS = "showKeys";

    static final String SAVEKEYS = "saveKeys";

    static final String SHOWHINTS = "showHints";

    @Inject
    private Logger logger;

    @Inject
    private Execution execution;

    @Inject
    private Container container;

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Override
    public Translation getTranslation(String key, Locale locale)
    {
        this.logger.debug("Translation Key [{}], locale [{}]", key, locale);
        Translation translation = super.getTranslation(key, locale);
        if (translation != null && !(translation instanceof ExtendedTranslation)) {
            if (this.container.getRequest() != null) {
                Map<String, Boolean> options = new HashMap<>();
                Request request = this.container.getRequest();
                options.put(SHOWKEYS, request.getProperty(SHOWKEYS) == null ? false : true);
                options.put(SAVEKEYS, request.getProperty(SAVEKEYS) == null ? false : true);
                options.put(SHOWHINTS, request.getProperty(SHOWHINTS) == null ? false : true);
                if (options.values().contains(true)) {
                    translation = new ExtendedTranslation(translation, options, this.execution.getContext(),
                        this.plainTextRenderer);
                }
            } else {
                this.logger.debug("No request for translation key [{}], locale [{}]", key, locale);
            }
        }
        return translation;
    }
}

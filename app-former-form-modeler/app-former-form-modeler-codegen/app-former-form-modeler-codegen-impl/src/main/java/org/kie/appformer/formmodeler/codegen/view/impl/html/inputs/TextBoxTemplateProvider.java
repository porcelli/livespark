/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.appformer.formmodeler.codegen.view.impl.html.inputs;


import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.kie.workbench.common.forms.model.impl.basic.textBox.TextBoxFieldDefinition;
import org.kie.workbench.common.forms.service.FieldManager;

/**
 * @author Pere Fernandez <pefernan@redhat.com>
 */
@Dependent
public class TextBoxTemplateProvider extends AbstractTemplateProvider {

    @Inject
    public TextBoxTemplateProvider( final FieldManager fieldManager ) {
        super( fieldManager );
    }

    @Override
    protected String[] getSupportedFieldCodes() {
        return new String[] { TextBoxFieldDefinition.CODE };
    }

    @Override
    protected String getTemplateForFieldTypeCode( final String fieldCode ) {
        return "/org/kie/appformer/formmodeler/codegen/view/impl/html/templates/textinput.mv";
    }
}

/*
 * Copyright 2012 Benjamin Glatzel <benjamin.glatzel@me.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.entitySystem.metadata.internal;

import com.google.common.collect.ImmutableList;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.metadata.ComponentLibrary;
import org.terasology.entitySystem.metadata.MetadataUtil;

import java.util.List;

/**
 * @author Immortius <immortius@gmail.com>
 */
public final class ComponentLibraryImpl extends BaseLibraryImpl<Component> implements ComponentLibrary {

    public ComponentLibraryImpl(MetadataBuilder metadataBuilder) {
        super(metadataBuilder);
    }

    @Override
    public List<String> getNamesFor(Class<? extends Component> clazz) {
        return ImmutableList.<String>builder()
                .add(clazz.getSimpleName())
                .add(MetadataUtil.getComponentClassName(clazz))
                .build();
    }
}
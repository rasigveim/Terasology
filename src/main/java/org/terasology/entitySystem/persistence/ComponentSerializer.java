package org.terasology.entitySystem.persistence;

import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.metadata.ClassMetadata;
import org.terasology.entitySystem.metadata.ComponentLibrary;
import org.terasology.entitySystem.metadata.MetadataUtil;
import org.terasology.entitySystem.metadata.FieldMetadata;
import org.terasology.protobuf.EntityData;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * ComponentSerializer provides the ability to serialize and deserialize between Components and the protobuf
 * EntityData.Component
 * <p/>
 * If provided with a componentIdTable, then the components will be serialized and deserialized using those ids rather
 * than the names of each component, saving some space.
 * <p/>
 * When serializing, a FieldSerializeCheck can be provided to determine whether each field should be serialized or not
 *
 * @author Immortius
 */
public class ComponentSerializer {

    private static final Logger logger = LoggerFactory.getLogger(ComponentSerializer.class);

    private ComponentLibrary componentLibrary;
    private BiMap<Class<? extends Component>, Integer> componentIdTable = ImmutableBiMap.<Class<? extends Component>, Integer>builder().build();

    /**
     * Creates the component serializer.
     *
     * @param componentLibrary The component library used to provide information on each component and its fields.
     */
    public ComponentSerializer(ComponentLibrary componentLibrary) {
        this.componentLibrary = componentLibrary;
    }

    /**
     * Sets the mapping between component classes and the ids that are used for serialization
     *
     * @param table
     */
    public void setComponentIdMapping(Map<Class<? extends Component>, Integer> table) {
        componentIdTable = ImmutableBiMap.copyOf(table);
    }

    /**
     * Clears the mapping between component classes and ids. This causes components to be serialized with their component
     * class name instead.
     */
    public void removeComponentIdMapping() {
        componentIdTable = ImmutableBiMap.<Class<? extends Component>, Integer>builder().build();
    }

    /**
     * @param componentData
     * @return The component described by the componentData, or null if it couldn't be deserialized
     */
    public Component deserialize(EntityData.Component componentData) {
        Class<? extends Component> componentClass = getComponentClass(componentData);
        if (componentClass != null) {
            ClassMetadata<? extends Component> componentMetadata = componentLibrary.getMetadata(componentClass);
            Component component = componentMetadata.newInstance();
            return deserializeOnto(component, componentData, componentMetadata);
        } else {
            logger.warn("Unable to deserialize unknown component type: {}", componentData.getType());
        }
        return null;
    }

    /**
     * Deserializes the componentData on top of the target component. Any fields that are not present in the componentData,
     * or which cannot be deserialized, are left unaltered.
     *
     * @param target
     * @param componentData
     * @return The target component.
     */
    public Component deserializeOnto(Component target, EntityData.Component componentData) {
        Class<? extends Component> componentClass = getComponentClass(componentData);
        if (componentClass != null) {
            ClassMetadata componentMetadata = componentLibrary.getMetadata(componentClass);
            return deserializeOnto(target, componentData, componentMetadata);
        } else {
            logger.warn("Unable to deserialize unknown component type: {}", componentData.getType());
        }
        return target;
    }


    private Component deserializeOnto(Component targetComponent, EntityData.Component componentData, ClassMetadata componentMetadata) {
        try {
            for (EntityData.NameValue field : componentData.getFieldList()) {
                FieldMetadata fieldInfo = componentMetadata.getField(field.getName());
                if (fieldInfo == null) {
                    continue;
                }

                Object value = fieldInfo.deserialize(field.getValue());
                if (value != null) {
                    fieldInfo.setValue(targetComponent, value);
                }
            }
            return targetComponent;
        } catch (InvocationTargetException e) {
            logger.error("Exception during serializing component type: {}", targetComponent.getClass(), e);
        } catch (IllegalAccessException e) {
            logger.error("Exception during serializing component type: {}", targetComponent.getClass(), e);
        }
        return targetComponent;
    }


    /**
     * Serializes a component.
     *
     * @param component
     * @return The serialized component, or null if it could not be serialized.
     */
    public EntityData.Component serialize(Component component) {
        return serialize(component, FieldSerializeCheck.NullCheck.newInstance());
    }

    /**
     * Serializes a component.
     *
     * @param component
     * @param check     A check to use to see if each field should be serialized.
     * @return The serialized component, or null if it could not be serialized.
     */
    public EntityData.Component serialize(Component component, FieldSerializeCheck check) {
        ClassMetadata<?> componentMetadata = componentLibrary.getMetadata(component.getClass());
        if (componentMetadata == null) {
            logger.error("Unregistered component type: {}", component.getClass());
            return null;
        }
        EntityData.Component.Builder componentMessage = EntityData.Component.newBuilder();
        serializeComponentType(component, componentMessage);

        for (FieldMetadata field : componentMetadata.iterateFields()) {
            if (check.shouldSerializeField(field, component)) {
                EntityData.NameValue fieldData = serializeField(field, component);
                if (fieldData != null) {
                    componentMessage.addField(fieldData);
                }
            }
        }

        return componentMessage.build();
    }

    private void serializeComponentType(Component component, EntityData.Component.Builder componentMessage) {
        Integer compId = componentIdTable.get(component.getClass());
        if (compId != null) {
            componentMessage.setTypeIndex(compId);
        } else {
            componentMessage.setType(MetadataUtil.getComponentClassName(component));
        }
    }

    private EntityData.NameValue serializeField(FieldMetadata field, Component component) {
        try {
            Object rawValue = field.getValue(component);
            if (rawValue == null) {
                return null;
            }

            EntityData.Value value = field.serialize(rawValue);
            if (value != null) {
                return EntityData.NameValue.newBuilder().setName(field.getName()).setValue(value).build();
            }
        } catch (IllegalAccessException e) {
            logger.error("Exception during serializing component type: {}", component.getClass(), e);
        } catch (InvocationTargetException e) {
            logger.error("Exception during serializing component type: {}", component.getClass(), e);
        }
        return null;
    }

    /**
     * Serializes the differences between two components.
     *
     * @param base  The base component to compare against.
     * @param delta The component whose differences will be serialized
     * @return The serialized component, or null if it could not be serialized
     */
    public EntityData.Component serialize(Component base, Component delta) {
        return serialize(base, delta, FieldSerializeCheck.NullCheck.newInstance());
    }

    /**
     * Serializes the differences between two components.
     *
     * @param base  The base component to compare against.
     * @param delta The component whose differences will be serialized
     * @param check A check to use to see if each field should be serialized.
     * @return The serialized component, or null if it could not be serialized
     */
    public EntityData.Component serialize(Component base, Component delta, FieldSerializeCheck check) {
        ClassMetadata<?> componentMetadata = componentLibrary.getMetadata(base.getClass());
        if (componentMetadata == null) {
            logger.error("Unregistered component type: {}", base.getClass());
            return null;
        }

        EntityData.Component.Builder componentMessage = EntityData.Component.newBuilder();
        serializeComponentType(delta, componentMessage);

        boolean changed = false;
        for (FieldMetadata field : componentMetadata.iterateFields()) {
            if (check.shouldSerializeField(field, delta)) {
                try {
                    Object origValue = field.getValue(base);
                    Object deltaValue = field.getValue(delta);

                    if (!Objects.equal(origValue, deltaValue)) {
                        EntityData.Value value = field.serialize(deltaValue);
                        if (value != null) {
                            componentMessage.addField(EntityData.NameValue.newBuilder().setName(field.getName()).setValue(value).build());
                            changed = true;
                        } else {
                            logger.error("Exception serializing component type: {}, field: {} - returned null", base.getClass(), field.getName());
                        }
                    }
                } catch (IllegalAccessException e) {
                    logger.error("Exception during serializing component type: {}", base.getClass(), e);
                } catch (InvocationTargetException e) {
                    logger.error("Exception during serializing component type: {}", base.getClass(), e);
                }
            }
        }

        if (changed) {
            return componentMessage.build();
        }

        return null;
    }

    /**
     * Determines the component class that the serialized component is for.
     *
     * @param componentData
     * @return The component class the given componentData describes, or null if it is unknown.
     */
    public Class<? extends Component> getComponentClass(EntityData.Component componentData) {
        if (componentData.hasTypeIndex()) {
            ClassMetadata metadata = null;
            if (!componentIdTable.isEmpty()) {
                Class<? extends Component> componentClass = componentIdTable.inverse().get(componentData.getTypeIndex());
                if (componentClass != null) {
                    metadata = componentLibrary.getMetadata(componentClass);
                }
            }
            if (metadata == null) {
                logger.warn("Unable to deserialize unknown component with id: {}", componentData.getTypeIndex());
                return null;
            }
            return (Class<? extends Component>) metadata.getType();
        } else if (componentData.hasType()) {
            ClassMetadata metadata = componentLibrary.getMetadata(componentData.getType());
            if (metadata == null) {
                logger.warn("Unable to deserialize unknown component type: {}", componentData.getType());
                return null;
            }
            return (Class<? extends Component>) metadata.getType();
        }
        logger.warn("Unable to deserialize component, no type provided.");

        return null;
    }
}
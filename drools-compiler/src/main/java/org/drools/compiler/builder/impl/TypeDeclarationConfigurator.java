/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.compiler.builder.impl;

import java.util.Map;

import org.drools.compiler.compiler.AnalysisResult;
import org.drools.compiler.compiler.BoundIdentifiers;
import org.drools.compiler.compiler.Dialect;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.compiler.TypeDeclarationError;
import org.drools.compiler.rule.builder.PackageBuildContext;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.rule.Annotated;
import org.drools.core.rule.TypeDeclaration;
import org.drools.drl.ast.descr.AbstractClassTypeDeclarationDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.kie.api.definition.type.Duration;
import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Timestamp;

import static org.drools.compiler.rule.builder.util.AnnotationFactory.toAnnotated;
import static org.drools.core.rule.TypeDeclaration.processTypeAnnotations;

public class TypeDeclarationConfigurator {

    protected final KnowledgeBuilderImpl kbuilder;

    public TypeDeclarationConfigurator( KnowledgeBuilderImpl kbuilder ) {
        this.kbuilder = kbuilder;
    }

    public void finalizeConfigurator(TypeDeclaration type, AbstractClassTypeDeclarationDescr typeDescr, PackageRegistry pkgRegistry, Map<String, PackageRegistry> pkgRegistryMap, ClassHierarchyManager hierarchyManager ) {
        // prefer definitions where possible
        if ( type.getNature() == TypeDeclaration.Nature.DEFINITION ) {
            hierarchyManager.addDeclarationToPackagePreservingOrder( type, typeDescr, pkgRegistry.getPackage(), pkgRegistryMap );
        } else {
            TypeDeclaration oldType = pkgRegistry.getPackage().getTypeDeclaration( type.getTypeName() );
            if ( oldType == null ) {
                pkgRegistry.getPackage().addTypeDeclaration( type );
            } else {
                if (type.getRole() == Role.Type.EVENT) {
                    oldType.setRole(Role.Type.EVENT);
                    if ( type.getDurationAttribute() != null ) {
                        oldType.setDurationAttribute( type.getDurationAttribute() );
                        oldType.setDurationExtractor( type.getDurationExtractor() );
                    }
                    if ( type.getTimestampAttribute() != null ) {
                        oldType.setTimestampAttribute( type.getTimestampAttribute() );
                        oldType.setTimestampExtractor( type.getTimestampExtractor() );
                    }
                    if ( type.getExpirationOffset() >= 0 ) {
                        oldType.setExpirationOffset( type.getExpirationOffset() );
                        oldType.setExpirationType( type.getExpirationPolicy() );
                    }
                }
                if (type.isPropertyReactive()) {
                    oldType.setPropertyReactive(true);
                }
            }
        }
    }


    public boolean wireFieldAccessors( PackageRegistry pkgRegistry,
                                       AbstractClassTypeDeclarationDescr typeDescr,
                                       TypeDeclaration type ) {

        if ( type.getTypeClassDef() != null ) {
            try {
                pkgRegistry.getPackage().buildFieldAccessors( type );
            } catch ( Throwable e ) {
                kbuilder.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error creating field accessors for TypeDeclaration '" + type.getTypeName() +
                                                                   "' for type '" +
                                                                   type.getTypeName() +
                                                                   " : " + e.getMessage() +
                                                                   "'"));
                return false;
            }
        }

        Annotated annotatedType = toAnnotated(typeDescr);
        processMvelBasedAccessors( kbuilder, pkgRegistry, annotatedType, type );
        processTypeAnnotations( type, annotatedType, kbuilder.getBuilderConfiguration().getPropertySpecificOption());
        return true;
    }

    static void processMvelBasedAccessors( KnowledgeBuilderImpl kbuilder, PackageRegistry pkgRegistry, Annotated annotated, TypeDeclaration type ) {
        wireTimestampAccessor( kbuilder, annotated, type, pkgRegistry );
        wireDurationAccessor( kbuilder, annotated, type, pkgRegistry );
    }

    private static void wireTimestampAccessor( KnowledgeBuilderImpl kbuilder, Annotated annotated, TypeDeclaration type, PackageRegistry pkgRegistry ) {
        Timestamp timestamp = annotated.getTypedAnnotation(Timestamp.class);
        if ( timestamp != null ) {
            BaseDescr typeDescr = annotated instanceof BaseDescr ? ( (BaseDescr) annotated ) : new BaseDescr();
            String timestampField;
            try {
                timestampField = timestamp.value();
            } catch (Exception e) {
                kbuilder.addBuilderResult(new TypeDeclarationError(typeDescr, e.getMessage()));
                return;
            }
            type.setTimestampAttribute( timestampField );
            InternalKnowledgePackage pkg = pkgRegistry.getPackage();

            AnalysisResult results = getMvelAnalysisResult( kbuilder, typeDescr, type, pkgRegistry, timestampField, pkg );
            if (results != null) {
                type.setTimestampExtractor(pkg.getFieldExtractor( type, timestampField, results.getReturnType() ));
            } else {
                kbuilder.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error creating field accessors for timestamp field '" + timestamp +
                                                                   "' for type '" + type.getTypeName() + "'"));
            }
        }
    }

    private static void wireDurationAccessor( KnowledgeBuilderImpl kbuilder, Annotated annotated, TypeDeclaration type, PackageRegistry pkgRegistry ) {
        Duration duration = annotated.getTypedAnnotation(Duration.class);
        if (duration != null) {
            BaseDescr typeDescr = annotated instanceof BaseDescr ? ( (BaseDescr) annotated ) : new BaseDescr();
            String durationField;
            try {
                durationField = duration.value();
            } catch (Exception e) {
                kbuilder.addBuilderResult(new TypeDeclarationError(typeDescr, e.getMessage()));
                return;
            }
            type.setDurationAttribute(durationField);
            InternalKnowledgePackage pkg = pkgRegistry.getPackage();

            AnalysisResult results = getMvelAnalysisResult( kbuilder, typeDescr, type, pkgRegistry, durationField, pkg );
            if (results != null) {
                type.setDurationExtractor(pkg.getFieldExtractor( type, durationField, results.getReturnType() ));
            } else {
                kbuilder.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error processing @duration for TypeDeclaration '" + type.getFullName() +
                                                                   "': cannot access the field '" + durationField + "'"));
            }
        }
    }

    private static AnalysisResult getMvelAnalysisResult( KnowledgeBuilderImpl kbuilder, BaseDescr typeDescr, TypeDeclaration type, PackageRegistry pkgRegistry, String durationField, InternalKnowledgePackage pkg ) {
        Dialect dialect = pkgRegistry.getDialectCompiletimeRegistry().getDialect("mvel");
        PackageBuildContext context = new PackageBuildContext();
        context.init(kbuilder, pkg, typeDescr, pkgRegistry.getDialectCompiletimeRegistry(), dialect, null);
        if (!type.isTypesafe()) {
            context.setTypesafe(false);
        }

        return context.getDialect().analyzeExpression( context,
                                                       typeDescr,
                                                       durationField,
                                                       new BoundIdentifiers( type.getTypeClass() ) );
    }
}

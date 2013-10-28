/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.ide.eclipse.core.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.model.IModuleResource;
import org.eclipse.wst.server.core.model.ModuleDelegate;
import org.eclipse.wst.server.core.util.ModuleFile;
import org.eclipse.wst.server.core.util.ProjectModuleFactoryDelegate;

public class SlingBundleModuleFactory extends ProjectModuleFactoryDelegate {

    static final String SLING_BUNDLE_FACET_ID = "sling.bundle";
	private static final IPath[] SETTINGS_PATHS = new IPath[] {new Path(".settings")};
    
    @Override
    protected IPath[] getListenerPaths() {
    	// returning the .settings path instead of null (as done by the parent)
    	// results in clearing the cache on changes to .settings - which in turn
    	// results in re-evaluating facet changes.
    	// we could be more specific here but .settings changes are infrequent anyway.
    	return SETTINGS_PATHS;
    }

    @Override
    public ModuleDelegate getModuleDelegate(IModule module) {

        return new SlingBundleModuleDelegate(module);
    }

    @Override
    protected IModule createModule(IProject project) {

        try {
            IFacetedProject facetedProject = ProjectFacetsManager.create(project);
            for (IProjectFacetVersion facet : facetedProject.getProjectFacets()) {
                if (facet.getProjectFacet().getId().equals(SLING_BUNDLE_FACET_ID)) {
                    return createModule(project.getName(), project.getName(), SLING_BUNDLE_FACET_ID, "1.0", project);
                }
            }
        } catch (CoreException ce) {
            // TODO logging
        }

        return null;
    }

    static class SlingBundleModuleDelegate extends ModuleDelegate {

        private final IModule module;

        public SlingBundleModuleDelegate(IModule module) {
            this.module = module;
        }

        @Override
        public IStatus validate() {
            return Status.OK_STATUS; // TODO actually validate
        }

        /**
         * This returns the list of module resources that make up the bundle.
         * <p>
         * This list is composed of all files which are not derived. Derived files
         * are those that are generated by m2eclipse/eclipse - and typically are 
         * the derived files and/or the files under target/classes (the output dirs).
         * <p>
         * This list is further down used as the input to SlingLaunchpadBehaviour
         * and there evaluated. Depending on that class's behavior, this method
         * might have to be adjusted (as for example in one version the SlingLaunchpadBehaviour
         * completely ignores the list of changed files and redeploys everything - if another
         * behaviour is used where only diffs are published, this members might have
         * to return exactly the opposite: everything derived..)
         */
        @Override
        public IModuleResource[] members() throws CoreException {
            IProject project = module.getProject();
            final IJavaProject javaProject = ProjectHelper.asJavaProject(project);
            final List<IModuleResource> resources = new ArrayList<IModuleResource>();
            
            final Set<String> filteredLocations = new HashSet<String>();

            final IJavaProject jp = javaProject;
            final IClasspathEntry[] rawCp = jp.getRawClasspath();
            for (int i = 0; i < rawCp.length; i++) {
				IClasspathEntry aCp = rawCp[i];
				IPath outputLocation = aCp.getOutputLocation();
				if (outputLocation!=null) {
					outputLocation = outputLocation.makeRelativeTo(project.getFullPath());
					filteredLocations.add(outputLocation.toString());
				}
			}
            
            project.accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) throws CoreException {

                    if (resource.getType() == IResource.PROJECT) {
                        return true;
                    }

                    final IPath relativePath = resource.getProjectRelativePath();
                    if (relativePath == null) {
                    	return false;
                    }
                    final String relPathStr = relativePath.toString();
                    if (relPathStr == null || relPathStr.length()==0) {
                    	return false;
                    }
                    if (resource.isDerived()) {
                    	// then dont accept it
                    	return false;
                    }
					if (filteredLocations.contains(relPathStr)) {
                    	return false;
                    }
					if (resource.getType() == IResource.FILE) {
						// the bundle facet accepts all files that are not in the output directory/derived
						ModuleFile moduleFile = new ModuleFile((IFile) resource, resource.getName(), relativePath);
						resources.add(moduleFile);
					}
                    return true;
                }
            });

            for (Iterator<IModuleResource> it = resources.iterator(); it.hasNext();) {
				IModuleResource iModuleResource = it.next();
				System.out.println(" ADDED: "+iModuleResource.getModuleRelativePath().toString());
				
			}
            return resources.toArray(new IModuleResource[resources.size()]);
        }

        @Override
        public IModule[] getChildModules() {
            return new IModule[0]; // TODO revisit, do we need child modules?
        }
    }
}

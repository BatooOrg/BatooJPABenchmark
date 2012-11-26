/*
 * Copyright (c) 2012 - Batoo Software ve Consultancy Ltd.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.batoo.jpa.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * A test class loader to map test persistence.xml's
 * 
 * @author hceylan
 * 
 * @since 2.0.0
 */
public class TestClassLoader extends ClassLoader {

	private static final String META_INF = "META-INF/";
	private static final String PERSISTENCE_XML = "persistence.xml";
	private static final String ORM_XML = "orm.xml";

	private static final String FULL_PERSISTENCE_XML = TestClassLoader.META_INF + TestClassLoader.PERSISTENCE_XML;
	private static final String FULL_ORM_XML = TestClassLoader.META_INF + TestClassLoader.ORM_XML;
	private final String db;
	private final String persistenceXmlPath;

	private final String ormXmlPath;

	/**
	 * @param parent
	 *            the parent class loader
	 * @param db
	 *            the db to use
	 * @since 2.0.0
	 */
	public TestClassLoader(String db, ClassLoader parent) {
		super();

		this.db = db;
		this.persistenceXmlPath = TestClassLoader.META_INF + this.db + "_" + TestClassLoader.PERSISTENCE_XML;
		this.ormXmlPath = TestClassLoader.META_INF + this.db + "_" + TestClassLoader.ORM_XML;
	}

	/**
	 * Returns the persistence xml path.
	 * 
	 * @return the persistence xml path
	 * 
	 * @since $version
	 */
	public String getPersistenceXmlPath() {
		return this.persistenceXmlPath;
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public InputStream getResourceAsStream(String name) {
		if (name.equals(TestClassLoader.FULL_PERSISTENCE_XML)) {
			return super.getResourceAsStream(this.persistenceXmlPath);
		}

		if (name.equals(TestClassLoader.FULL_ORM_XML)) {
			return super.getResourceAsStream(this.ormXmlPath);
		}

		return super.getResourceAsStream(name);
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		if (name.equals(TestClassLoader.FULL_PERSISTENCE_XML)) {
			name = TestClassLoader.META_INF + this.db + "_" + TestClassLoader.PERSISTENCE_XML;
			return super.getResources(name);
		}

		if (name.equals(TestClassLoader.FULL_ORM_XML)) {
			name = TestClassLoader.META_INF + this.db + "_" + TestClassLoader.ORM_XML;

			return super.getResources(name);
		}

		return super.getResources(name);
	}
}

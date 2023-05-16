/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.schema;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.BaseReactiveTest;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.reactive.testing.DBSelectionExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.vertx.junit5.VertxTestContext;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import static org.hibernate.reactive.containers.DatabaseConfiguration.DBType.SQLSERVER;
import static org.hibernate.tool.schema.JdbcMetadaAccessStrategy.GROUPED;
import static org.hibernate.tool.schema.JdbcMetadaAccessStrategy.INDIVIDUALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public abstract class SchemaUpdateSqlServerTestBase extends BaseReactiveTest {

	private static final String DEFAULT_CATALOG_NAME = "master";

	/**
	 * Test INDIVIDUALLY option without setting the default catalog name
	 */
	public static class IndividuallySchemaUpdateSqlServerTest extends SchemaUpdateSqlServerTestBase {

		@Override
		protected Configuration constructConfiguration(String hbm2DdlOption) {
			final Configuration configuration = super.constructConfiguration( hbm2DdlOption );
			configuration.setProperty( Settings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY, INDIVIDUALLY.toString() );
			return configuration;
		}
	}

	/**
	 * Test INDIVIDUALLY option when we set the catalog name to the default name
	 */
	public static class IndividuallySchemaUpdateWithCatalogTest extends SchemaUpdateSqlServerTestBase {

		@Override
		protected Configuration constructConfiguration(String hbm2DdlOption) {
			final Configuration configuration = super.constructConfiguration( hbm2DdlOption );
			configuration.setProperty( Settings.DEFAULT_CATALOG, DEFAULT_CATALOG_NAME );
			return configuration;
		}

		@Override
		public String addCatalog(String name) {
			return DEFAULT_CATALOG_NAME + "." + name;
		}
	}

	/**
	 * Test GROUPED option without setting the default catalog name
	 */
	public static class GroupedSchemaUpdateSqlServerTest extends SchemaUpdateSqlServerTestBase {

		@Override
		protected Configuration constructConfiguration(String hbm2DdlOption) {
			final Configuration configuration = super.constructConfiguration( hbm2DdlOption );
			configuration.setProperty( Settings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY, GROUPED.toString() );
			return configuration;
		}
	}

	/**
	 * Test GROUPED option when we set the catalog name to default name
	 */
	public static class GroupedSchemaUpdateWithCatalogNameTest extends SchemaUpdateSqlServerTestBase {

		@Override
		protected Configuration constructConfiguration(String hbm2DdlOption) {
			final Configuration configuration = super.constructConfiguration( hbm2DdlOption );
			configuration.setProperty( Settings.DEFAULT_CATALOG, DEFAULT_CATALOG_NAME );
			return configuration;
		}

		@Override
		public String addCatalog(String name) {
			return DEFAULT_CATALOG_NAME + "." + name;
		}
	}

	protected Configuration constructConfiguration(String hbm2DdlOption) {
		Configuration configuration = constructConfiguration();
		configuration.setProperty( Settings.HBM2DDL_AUTO, hbm2DdlOption );
		configuration.setProperty( Settings.DEFAULT_SCHEMA, "dbo" );
		return configuration;
	}

	@RegisterExtension
	public DBSelectionExtension dbRule = DBSelectionExtension.runOnlyFor( SQLSERVER );

	public String addCatalog(String name) {
		return name;
	}

	@BeforeEach
	@Override
	public void before(VertxTestContext context) {
		Configuration createHbm2ddlConf = constructConfiguration( "create" );
		createHbm2ddlConf.addAnnotatedClass( ASimpleFirst.class );
		createHbm2ddlConf.addAnnotatedClass( AOther.class );

		test( context, dropSequenceIfExists( createHbm2ddlConf )
				.thenCompose( ignore -> setupSessionFactory( createHbm2ddlConf )
				.thenCompose( v -> factoryManager.stop() ) ) );
	}

	// See HHH-14835: Vert.x throws an exception when the catalog is specified.
	// Because it happens during schema creation, the error is ignored and the build won't fail
	// if one of the previous tests has already created the sequence.
	// This method makes sure that the sequence is deleted if it exists, so that these tests
	// fail consistently when the wrong ORM version is used.
	private CompletionStage<Void> dropSequenceIfExists(Configuration createHbm2ddlConf) {
		return setupSessionFactory( createHbm2ddlConf )
				.thenCompose( v -> getSessionFactory()
						.withTransaction( (session, transaction) -> session
								// No need to add the catalog name because MSSQL doesn't support it
								.createNativeQuery( "drop sequence if exists dbo.hibernate_sequence" )
								.executeUpdate() ) )
				.handle( (res, err) -> null )
				.thenCompose( v -> factoryManager.stop() );
	}

	@Override
	@AfterEach
	public void after(VertxTestContext context) {
		final Configuration dropHbm2ddlConf = constructConfiguration( "drop" );
		dropHbm2ddlConf.addAnnotatedClass( ASimpleNext.class );
		dropHbm2ddlConf.addAnnotatedClass( AOther.class );
		dropHbm2ddlConf.addAnnotatedClass( AAnother.class );

		test( context, factoryManager.stop()
				.thenCompose( v -> setupSessionFactory( dropHbm2ddlConf ) )
				.thenCompose( v -> factoryManager.stop() ) );
	}

	@Test
	public void testValidationSucceed(VertxTestContext context) {
		Configuration configuration = constructConfiguration( "validate" );
		configuration.addAnnotatedClass( ASimpleFirst.class );
		configuration.addAnnotatedClass( AOther.class );

		test( context, setupSessionFactory( configuration ) );
	}

	@Test
	public void testUpdate(VertxTestContext context) {
		final String indexDefinitionQuery =
				"select COL_NAME(ic.object_id, ic.column_id), ic.is_descending_key " +
					"from sys.indexes i inner join sys.index_columns ic " +
					"on ic.object_id = i.object_id and ic.index_id = i.index_id " +
					"where OBJECT_NAME(i.object_id) = ? and i.name = ? and i.is_unique = ? " +
					"order by ic.key_ordinal";

		final String foreignKeyDefinitionQuery =
				"select COL_NAME( parent_object_id, parent_column_id ) as col_name, " +
						"COL_NAME( referenced_object_id, referenced_column_id) as ref_col_name " +
						"from sys.foreign_key_columns " +
						"where OBJECT_NAME( constraint_object_id ) = ? " +
						"and OBJECT_NAME( parent_object_id ) = ? " +
						"and OBJECT_NAME( referenced_object_id ) = ? " +
						"order by constraint_column_id";

		final ASimpleNext aSimple = new ASimpleNext();
		aSimple.aValue = 9;
		aSimple.aStringValue = "abc";
		aSimple.data = "Data";

		final AOther aOther = new AOther();
		aOther.id1 = 1;
		aOther.id2 = "other";
		aOther.anotherString = "another";

		final AAnother aAnother = new AAnother();
		aAnother.description = "description";

		aSimple.aOther = aOther;
		aSimple.aAnother = aAnother;

		final Configuration updateHbm2ddlConf = constructConfiguration( "update" );
		updateHbm2ddlConf.addAnnotatedClass( ASimpleNext.class );
		updateHbm2ddlConf.addAnnotatedClass( AOther.class );
		updateHbm2ddlConf.addAnnotatedClass( AAnother.class );
		test(
				context,
				setupSessionFactory( updateHbm2ddlConf )
						.thenCompose( v -> getSessionFactory()
								.withTransaction( (session, t) -> session.persist( aSimple ) ) )
						.thenCompose( v1 -> openSession()
								.thenCompose( s -> s
										.find( ASimpleNext.class, aSimple.id )
										.thenAccept( result -> {
											assertNotNull( result );
											assertEquals( aSimple.aValue, result.aValue );
											assertEquals( aSimple.aStringValue, result.aStringValue );
											assertEquals( aSimple.data, result.data );
											assertNotNull( result.aOther );
											assertEquals( aOther.id1, result.aOther.id1 );
											assertEquals( aOther.id2, result.aOther.id2 );
											assertEquals( aOther.anotherString, result.aOther.anotherString );
											assertNotNull( result.aAnother );
											assertEquals( aAnother.description, result.aAnother.description );
										} )
										.thenCompose( v -> s.createNativeQuery( indexDefinitionQuery )
												.setParameter( 1, "ASimple" )
												.setParameter( 2, "i_asimple_avalue_astringValue" )
												.setParameter( 3, 0 )
												.getResultList()
												.thenAccept( list -> {
													assertEquals( 2, list.size() );
													assertEquals( "aValue", ( (Object[]) list.get(0) )[0] );
													assertEquals( false, ( (Object[]) list.get(0) )[1] );
													assertEquals( "aStringValue", ( (Object[]) list.get(1) )[0] );
													assertEquals( true, ( (Object[]) list.get(1) )[1] );
												} )
										)
										.thenCompose( v -> s.createNativeQuery( indexDefinitionQuery )
												.setParameter( 1, "ASimple" )
												.setParameter( 2, "i_asimple_avalue_data" )
												.setParameter( 3, 0 )
												.getResultList()
												.thenAccept( list -> {
													assertEquals( 2, list.size() );
													assertEquals( "aValue", ( (Object[]) list.get(0) )[0] );
													assertEquals( true, ( (Object[]) list.get(0) )[1] );
													assertEquals( "data", ( (Object[]) list.get(1) )[0] );
													assertEquals( false, ( (Object[]) list.get(1) )[1] );
												} )
										)
										.thenCompose( v -> s.createNativeQuery( indexDefinitionQuery )
												.setParameter( 1, "ASimple" )
												.setParameter( 2, "u_asimple_astringvalue" )
												.setParameter( 3, 1 )
												.getResultList()
												.thenAccept( list -> {
													assertEquals( 1, list.size() );
													assertEquals( "aStringValue", ( (Object[]) list.get(0) )[0] );
													assertEquals( false, ( (Object[]) list.get(0) )[1] );
												} )
										)
										.thenCompose( v -> s.createNativeQuery( foreignKeyDefinitionQuery )
												.setParameter( 1, "fk_asimple_aother" )
												.setParameter( 2, "ASimple" )
												.setParameter( 3, "AOther" )
												.getResultList()
												.thenAccept( results -> {
													assertEquals( 2, results.size() );
													assertEquals( "id1", ( (Object[]) results.get( 0 ) )[0] );
													assertEquals( "id1", ( (Object[]) results.get( 0 ) )[1] );
													assertEquals( "id2", ( (Object[]) results.get( 1 ) )[0] );
													assertEquals( "id2", ( (Object[]) results.get( 1 ) )[1] );
												} )
										)
										.thenCompose( v -> s.createNativeQuery( foreignKeyDefinitionQuery )
												.setParameter( 1, "fk_asimple_aanother" )
												.setParameter( 2, "ASimple" )
												.setParameter( 3, "AAnother" )
												.getSingleResult()
												.thenAccept( result -> {
													assertEquals( "aAnother_id", ( (Object[]) result )[0] );
													assertEquals( "id", ( (Object[]) result )[1] );
												} )
										)
								) )
		);

	}

	@Entity(name = "ASimple")
	@Table(name = "ASimple", indexes = @Index(
			name = "i_asimple_avalue_astringValue",
			columnList = "aValue ASC, aStringValue DESC"
	))
	public static class ASimpleFirst {
		@Id
		@GeneratedValue
		private Integer id;
		private Integer aValue;
		private String aStringValue;
		@ManyToOne(cascade = CascadeType.ALL)
		@JoinColumns(
				value = { @JoinColumn(name = "id1"), @JoinColumn(name = "id2") },
				foreignKey = @ForeignKey(name = "fk_asimple_aother")
		)
		private AOther aOther;
	}

	@Entity(name = "ASimple")
	@Table(name = "ASimple", indexes = {
			@Index(name = "i_asimple_avalue_astringValue", columnList = "aValue ASC, aStringValue DESC"),
			@Index(name = "i_asimple_avalue_data", columnList = "aValue DESC, data ASC")
	},
			uniqueConstraints = { @UniqueConstraint(name = "u_asimple_astringvalue", columnNames = "aStringValue") }
	)
	public static class ASimpleNext {
		@Id
		@GeneratedValue
		private Integer id;

		private Integer aValue;

		private String aStringValue;

		private String data;

		@ManyToOne(cascade = CascadeType.ALL)
		@JoinColumns(
				value = { @JoinColumn(name = "id1"), @JoinColumn(name = "id2") },
				foreignKey = @ForeignKey(name = "fk_asimple_aother")
		)
		private AOther aOther;

		@ManyToOne(cascade = CascadeType.ALL)
		@JoinColumn(foreignKey = @ForeignKey(name = "fk_asimple_aanother"))
		private AAnother aAnother;
	}

	@Entity(name = "AOther")
	@IdClass(AOtherId.class)
	public static class AOther {
		@Id
		private int id1;

		@Id
		private String id2;

		private String anotherString;
	}

	public static class AOtherId implements Serializable {
		private int id1;
		private String id2;

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			AOtherId aOtherId = (AOtherId) o;
			return id1 == aOtherId.id1 && id2.equals( aOtherId.id2 );
		}

		@Override
		public int hashCode() {
			return Objects.hash( id1, id2 );
		}
	}

	@Entity(name = "AAnother")
	public static class AAnother {
		@Id
		@GeneratedValue
		private Integer id;

		private String description;
	}
}

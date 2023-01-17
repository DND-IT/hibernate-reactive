/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive;

import java.util.Collection;
import java.util.List;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.testing.DatabaseSelectionRule;

import org.junit.Rule;
import org.junit.Test;

import io.vertx.ext.unit.TestContext;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static org.hibernate.reactive.containers.DatabaseConfiguration.DBType.COCKROACHDB;
import static org.hibernate.reactive.testing.DatabaseSelectionRule.skipTestsFor;


/**
 * Test supported types for ids generated by the database
 * <p>
 *     CockroachDB is tested via {@link IdentityGeneratorTypeForCockroachDBTest}.
 * </p>
 * @see IdentityGeneratorTest
 */
public class IdentityGeneratorTypeTest extends BaseReactiveTest {

	// CockroachDB ids cannot be cast to short or int (they are too big)
	@Rule
	public DatabaseSelectionRule skip = skipTestsFor( COCKROACHDB );

	@Override
	protected Collection<Class<?>> annotatedEntities() {
		return List.of( IntegerTypeEntity.class, LongTypeEntity.class );
	}

	/**
	 * When {@link AvailableSettings#USE_GET_GENERATED_KEYS} is enabled, different
	 * queries will be used for each datastore to get the id
	 */
	public static class EnableUseGetGeneratedKeys extends IdentityGeneratorTypeTest {

		@Override
		protected Configuration constructConfiguration() {
			Configuration configuration = super.constructConfiguration();
			configuration.setProperty( AvailableSettings.USE_GET_GENERATED_KEYS, "true" );
			return configuration;
		}
	}

	@Override
	protected Configuration constructConfiguration() {
		Configuration configuration = super.constructConfiguration();
		// It's the default, but I want to highlight what we are testing
		configuration.setProperty( AvailableSettings.USE_GET_GENERATED_KEYS, "false" );
		return configuration;
	}

	private <U extends Number, T extends TypeIdentity<U>> void assertType(
			TestContext context,
			Class<T> entityClass,
			T entity,
			U expectedId) {
		test( context, getMutinySessionFactory()
				.withSession( s -> s.persist( entity ).call( s::flush )
						.invoke( () -> {
							context.assertNotNull( entity.getId() );
							context.assertEquals( entity.getId(), expectedId );
						} ) )
				.chain( () -> getMutinySessionFactory()
						.withSession( s -> s.find( entityClass, entity.getId() )
								.invoke( result -> {
									context.assertNotNull( result );
									context.assertEquals( result.getId(), entity.getId() );
								} ) ) )
		);
	}

	@Test
	public void longIdentityType(TestContext context) {
		assertType( context, LongTypeEntity.class, new LongTypeEntity(), 1L );
	}

	@Test
	public void integerIdentityType(TestContext context) {
		assertType( context, IntegerTypeEntity.class, new IntegerTypeEntity(), 1 );
	}

	interface TypeIdentity<T extends Number> {
		T getId();
	}

	@Entity(name = "IntegerTypeEntity")
	@Table(name = "IntegerTypeEntity")
	static class IntegerTypeEntity implements TypeIdentity<Integer> {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		public Integer id;

		@Override
		public Integer getId() {
			return id;
		}
	}

	@Entity(name = "LongTypeEntity")
	@Table(name = "LongTypeEntity")
	static class LongTypeEntity implements TypeIdentity<Long> {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		public Long id;

		@Override
		public Long getId() {
			return id;
		}
	}
}

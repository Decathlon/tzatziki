Tzatziki Spring JPA Library
======

## Description

This module provides the base dependencies to start testing your Spring App if you use JPA

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-spring-jpa</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Please note that if you are using JSONB in your entities, you need to exclude a transitive dependency depending on which persistence API you're using in your application.
If using Java Persistence (@Table is taken from javax.persistence):
```xml
<exclusions>
    <exclusion>
        <artifactId>jackson-datatype-hibernate5-jakarta</artifactId>
        <groupId>com.fasterxml.jackson.datatype</groupId>
    </exclusion>
</exclusions>
```
If using Jakarta (@Table is taken from jakarta.persistence):
```xml
<exclusions>
    <exclusion>
        <artifactId>jackson-datatype-hibernate5</artifactId>
        <groupId>com.fasterxml.jackson.datatype</groupId>
    </exclusion>
</exclusions>
```
It will prevent com.vladmihalcea.hibernate-types-* / io.hypersistence.hypersistence-utils-hibernate-* mapper from having conflict on which Module to use for (de)serialization

## Adding the datasource configuration

we will assume that you followed the [readme from the spring module](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring)

The only thing you need to do is to add the test container instance and the datasource configuration to your test Steps.

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = Application.class)
@ContextConfiguration(initializers = ApplicationSteps.Initializer.class)
public class ApplicationSteps {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:12").withTmpFs(Maps.of("/var/lib/postgresql/data", "rw"));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            postgres.start();
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword()
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
```

*Note: The code mostly uses Spring classes to manipulate the database so this should work with most vendors.*

## Inserting data

You will need a CrudRespository<YourEntity> for it work. If you don't have one for your production code,
you can still create one locally for your test code, this will work just fine. 

Assuming the following entity class:
```java
@NoArgsConstructor
@Getter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(name = "first_name")
    String firstName;

    @Column(name = "last_name")
    String lastName;
}
```

You can now insert rows with:
```gherkin
Given that the users table will contain:
  | id | firstName | lastName |
  | 1  | Darth     | Vader    |

# or alternatively
Given that the User entities will contain:
  """
  - id: 1
    firstName: Darth
    lastName: Vader
  """
# single rows work as well  
Given that the users table will contain:
  """
  id: 1
  firstName: Darth
  lastName: Vader
  """
# or Json  
Given that the UserRepository repository will contain:
  """
  [
    {
      "id": 1,
      "firstName": "Darth",
      "lastName": "Vader"
    }
  ]
  """
```

Adding `only` to the step will also empty the table before inserting the data:
```gherkin
But when the users table will contain only:
  | id | firstName | lastName |
  | 1  | Han       | Solo     |
```

## Asserting data

You can assert the content of the database with:
```gherkin
Then the users table contains:
  | id | firstName | lastName |
  | 1  | Darth     | Vader    |

# or alternatively
Then the User entities contains:
  """
  id: 1
  firstName: Darth
  lastName: Vader
  """
# many rows, in Json or Yaml or Table, like for the insert.
Then the UserRepository repository contains:
  ...
```

To assert that a table is empty:
```gherkin
Then the users table contains nothing
```

Consistent with the other steps in the Core and Http module, you can assert that the table contains `only` `exactly` 
or `at least` and possibly `in order` the given rows, as a table, a YAML or a Json.

The library will also attempt to look up a table, an entity or a repository matching your step. 

By default, the triggers will be disabled when you insert your rows, so that you can specify even the generated values.
However, if you wish you can re-enable them with:

```gherkin
But if the triggers are enabled
```

## Getting data

You can get the content of the database with:
```gherkin
Then content is the users table content

# or alternatively
Then content is the User entities
```

The `content` variable created will be a List created from `org.springframework.data.repository.CrudRepository#findAll` 

For both you can order the List fetched from the database (direction is not mandatory and defaults to ascending)
```gherkin
Then content is the users table content ordered by date_of_birth desc and date_of_death

# or alternatively
Then content is the User entities ordered by date_of_birth desc and date_of_death
```

The `content` variable created will be a List created from `org.springframework.data.jpa.repository.JpaRepository#findAll(Sort)`

## Resetting the database between tests

The library will automatically reset the database between each test. 
It will run `TRUNCATE <table> RESTART IDENTITY CASCADE` for each table and re-enable the triggers if you have disabled them.
If you need more, feel free to autowire the DataSource in your local steps. 

if you do not want to reset the database between the tests, or you prefer to do it yourself, you can alter this with:

```java
SpringJPASteps.autoclean = false; // to turn it off completely
SpringJPASteps.schemaToClean = "you-schema"; // the default is 'public'
DatabaseCleaner.addToTablesNotToBeCleaned("config"); // this will prevent the config table to be cleaned
DatabaseCleaner.resetTablesNotToBeCleanedFilter(); // to purge the previously added tables
```

# More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/blob/main/tzatziki-spring-jpa/src/test/resources/com/decathlon/tzatziki/steps/spring-jpa.feature

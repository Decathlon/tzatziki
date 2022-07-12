Tzatziki Spring MongoDB
======

## Description

This module provides the base dependencies to start testing your Spring App if you use MongoDB as persistence layer

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-spring-mongodb</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## Adding the datasource configuration

we will assume that you followed the [readme from the spring module](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring)

The only thing you need to do is to add the test container instance and the datasource configuration to your test Steps.

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MongoApplication.class)
@ContextConfiguration(initializers = TestMongoSteps.Initializer.class)
@ActiveProfiles({"test"})
@Slf4j
public class TestMongoSteps {

    final static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.2-bionic"));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

            mongoDBContainer.start();
            TestPropertyValues.of("spring.data.mongodb.uri=" + mongoDBContainer.getReplicaSetUrl())
                    .applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
```

## Inserting data

You will need a MongoRepository for it work. If you don't have one for your production code,
you can still create one locally for your test code, this will work just fine. 

Assuming the following entity class:
```java
@Document(value = "users")
@Getter
@Setter
public class User {

    @Id
    public String id;
    public String firstName;
    public String lastName;
}
```

You can now insert rows with:
```gherkin
Given that the users document will contain:
  | id | firstName | lastName |
  | 1  | Darth     | Vader    |

# or alternatively
Given that the User entities will contain:
  """
  - id: 1
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

Adding `only` to the step will also empty the document before inserting the data:
```gherkin
But when the users document will contain only:
  | id | firstName | lastName |
  | 1  | Han       | Solo     |
```

## Asserting data

You can assert the content of the database with:
```gherkin
Then the users document contains:
  | id | firstName | lastName |
  | 1  | Darth     | Vader    |

# or alternatively
Then the User entities contains:
  """
  id: 1
  firstName: Darth
  lastName: Vader
  """
```

To assert that a table is empty:
```gherkin
Then the users document contains nothing
```

Consistent with the other steps in the Core and Http module, you can assert that the document contains `only` `exactly` 
or `at least` and possibly `in order` the given rows, as a table, a YAML or a Json.

# More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/blob/main/tzatziki-spring-mongodb/src/test/resources/features/spring-mongo.feature

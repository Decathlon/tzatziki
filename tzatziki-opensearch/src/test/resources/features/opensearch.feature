Feature: Interact with a spring boot application that uses OpenSearch as a persistence layer

  Background:

  Scenario: Define users index and insert a user document
    Given that the users index is:
    """json
    {
      "settings": {
        "number_of_shards": "1",
        "number_of_replicas": "2"
      },
      "mappings": {
        "properties": {
          "firstName": {
            "type": "keyword"
          },
          "lastName": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the users index will contain:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

    Then the users index contains:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

  Scenario: Test index mapping verification
    Given that the products index is:
    """json
    {
      "mappings": {
        "properties": {
          "name": {
            "type": "text"
          },
          "price": {
            "type": "double"
          },
          "category": {
            "type": "keyword"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the products index mapping is:
    """json
    {
      "properties": {
        "name": {
          "type": "text"
        },
        "price": {
          "type": "double"
        },
        "category": {
          "type": "keyword"
        },
        "tags": {
          "type": "keyword"
        }
      }
    }
    """

  Scenario: Insert multiple documents with custom IDs
    Given that the orders index is:
    """json
    {
      "settings": {
        "number_of_shards": "1"
      },
      "mappings": {
        "properties": {
          "orderId": {
            "type": "keyword"
          },
          "amount": {
            "type": "double"
          },
          "status": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the orders index will contain:
      | _id    | orderId | amount | status    |
      | order1 | ORD001  | 99.99  | completed |
      | order2 | ORD002  | 149.50 | pending   |
      | order3 | ORD003  | 75.25  | cancelled |

    Then the orders index contains:
      | orderId | amount | status    |
      | ORD001  | 99.99  | completed |
      | ORD002  | 149.50 | pending   |
      | ORD003  | 75.25  | cancelled |

  Scenario: Test different comparison modes
    Given that the inventory index is:
    """json
    {
      "mappings": {
        "properties": {
          "productId": {
            "type": "keyword"
          },
          "quantity": {
            "type": "integer"
          }
        }
      }
    }
    """
    Given that the inventory index will contain:
      | _id | productId | quantity |
      | 1   | PROD001   | 50       |
      | 2   | PROD002   | 25       |
      | 3   | PROD003   | 100      |

    Then the inventory index contains exactly:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |
      | PROD003   | 100      |

    Then the inventory index still contains:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |

  Scenario: Test nested objects and complex data types
    Given that the articles index is:
    """json
    {
      "mappings": {
        "properties": {
          "title": {
            "type": "text"
          },
          "author": {
            "properties": {
              "name": {
                "type": "keyword"
              },
              "email": {
                "type": "keyword"
              }
            }
          },
          "publishDate": {
            "type": "date"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the articles index will contain:
      | _id | title                    | author                                      | publishDate | tags                    |
      | 1   | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

    Then the articles index contains:
      | title                    | author                                      | publishDate | tags                    |
      | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

  Scenario: Test empty index
    Given that the empty_test index is:
    """json
    {
      "mappings": {
        "properties": {
          "field1": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the empty_test index contains exactly:
      | field1 |


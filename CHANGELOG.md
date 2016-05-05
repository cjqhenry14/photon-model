# CHANGELOG

## 0.3.3

* Add ScheduledTaskService to run tasks periodically

* Remove all template factory services from resource services,
  replace them with concise FactoryService.createFactory() pattern

* Resource services (the model) no longer enforce that the self link
  is derived from the id field in the initial state. The resource allocation
  task still creates links that match the id, but that is no longer
  validated or required by the resources.

## 0.3.2

* AWS stats service implementation

* Handle OData queries in ComputeService

## 0.3.1

* Make resource service factories idempotent

* Add abstractions for monitoring service

## 0.3.0

* Move build process to maven

* Use junit for testing

* Refactor network and firewal services to move state away from their
task services

* Follow coding conventions laid out by the xenon project

* Add additional tests for the AWS adapter
 
## 0.2.2

* Start of CHANGELOG. See commit history.

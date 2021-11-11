# TODO backlog

# Done:
* default tab to be of the topic type
* return the key/value types from the publish page to the config page


## finish publish page
 * add `from topic` drop-down
 * implement --refresh-- (or 'randomize'?) to:
    * randomize json values for uploaded json
 * clean up UI interactions
 * put in repeat and partition sections

## fix running page
* fix it to show the recently submitted tasks
* add std out/std err in the REST request to view that output

## clean up expression UI
 * it's just ugly
 * add support for other languages (e.g. javascript)

## implement `tail` page
 a scrolling table of values for a topic:
  - can SerDe *any* type
  - show the key/value types
  - filter either all values or <key>:<value>s
  - support `freeze` (e.g. hold, pause)

## production support
 * add `submit on start` option to execute a config on startup
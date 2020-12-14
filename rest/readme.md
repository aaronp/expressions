# REST design

## Check mapping

See TransformRequest - the ability to see how a transformation mapping can turn a'Context' into 
some result type (e.g. json document, http request, etc).

Mapping scripts work great to for black-box transformations of a whole 'Context' into some result.

It can be used by many-to-many mappings (e.g. a batch of records to a collection of results)

### Request
POST /rest/mapping/check
{
  script : "script text",
  input : { ... arbitrary json }
}


## Check Config

The config option is a little less powerful than the 'mapping', but it can be simpler, as it supports use-cases
where a context is used to populate a general template (presumably from a config).

for example:

```aidl
  url : "http://{{ context.env.HOST }}/rest/foo/{{ context.request.key}}"
  method : POST
  body : "{ "something" : { {{ context.request.value }} }  }"
```

See TransformRequest - in this case the 'script' is taken to be the full config - as in the above example.

### Request
POST /rest/template/check
{
  script : "the config text",
  input : { ... arbitrary json }
}

## Get Config

This way the user can check the current configuration.

It should be in the form:

```aidl
app {
  kafka {
   ... some kafka config
  }
  
  rest : {
    <topic> : { ... either the template or mapping config }
  }
}
```

### Request
GET /rest/config


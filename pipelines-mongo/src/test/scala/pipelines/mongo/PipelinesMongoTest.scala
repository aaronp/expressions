package pipelines.mongo

import pipelines.mongo.audit.AuditServiceMongoSpec
import pipelines.mongo.users.{RefDataMongoSpec, UserServiceMongoSpec, UserAuthServiceSpec}

class PipelinesMongoTest
    extends BasePipelinesMongoSpec
    with MongoConnectSpec
    with AuditServiceMongoSpec
    with RefDataMongoSpec
    with UserServiceMongoSpec
    with UserAuthServiceSpec
    with LowPriorityMongoImplicitsSpec

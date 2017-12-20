/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module procedurecall-service-js/procedure_call_service */
!function (factory) {
  if (typeof require === 'function' && typeof module !== 'undefined') {
    factory();
  } else if (typeof define === 'function' && define.amd) {
    // AMD loader
    define('procedurecall-service-js/procedure_call_service-proxy', [], factory);
  } else {
    // plain old include
    ProcedureCallService = factory();
  }
}(function () {

  /**

 @class
  */
  var ProcedureCallService = function(eb, address) {

    var j_eb = eb;
    var j_address = address;
    var closed = false;
    var that = this;
    var convCharCollection = function(coll) {
      var ret = [];
      for (var i = 0;i < coll.length;i++) {
        ret.push(String.fromCharCode(coll[i]));
      }
      return ret;
    };

    /**
     Get some data from DAO.

     @public
     @param jsonObject {Object} 
     @param resultHandler {function} The result of the readSomethingFromDb operation. Use <code>AsyncResult.succeeded()</code> for success/failure detection. 
     */
    this.readSomethingFromDb = function(jsonObject, resultHandler) {
      var __args = arguments;
      if (__args.length === 2 && typeof __args[0] === 'object' && typeof __args[1] === 'function') {
        if (closed) {
          throw new Error('Proxy is closed');
        }
        j_eb.send(j_address, {"jsonObject":__args[0]}, {"action":"readSomethingFromDb"}, function(err, result) { __args[1](err, result &&result.body); });
        return;
      } else throw new TypeError('function invoked with invalid arguments');
    };

    /**
     Get Plant Stakeholder data from DAO.

     @public
     @param jsonObject {Object} 
     @param resultHandler {function} The result of the readSomethingFromDb operation. Use <code>AsyncResult.succeeded()</code> for success/failure detection. 
     */
    this.getPlantStakeholder = function(jsonObject, resultHandler) {
      var __args = arguments;
      if (__args.length === 2 && typeof __args[0] === 'object' && typeof __args[1] === 'function') {
        if (closed) {
          throw new Error('Proxy is closed');
        }
        j_eb.send(j_address, {"jsonObject":__args[0]}, {"action":"getPlantStakeholder"}, function(err, result) { __args[1](err, result &&result.body); });
        return;
      } else throw new TypeError('function invoked with invalid arguments');
    };

    /**
     Get available functions list from DAO.

     @public
     @param resultHandler {function} The result of the tableFunctionList operation. Use <code>AsyncResult.succeeded()</code> for success/failure detection. 
     */
    this.tableFunctionList = function(resultHandler) {
      var __args = arguments;
      if (__args.length === 1 && typeof __args[0] === 'function') {
        if (closed) {
          throw new Error('Proxy is closed');
        }
        j_eb.send(j_address, {}, {"action":"tableFunctionList"}, function(err, result) { __args[0](err, result &&result.body); });
        return;
      } else throw new TypeError('function invoked with invalid arguments');
    };

  };

  /**
   Creates an instance of the ProcedureCallService

   @memberof module:procedurecall-service-js/procedure_call_service
   @param dao {todo} 
   @param dataSource {todo} 
   @return {todo} a new ProcedureCallService instance, ready for use.
   */
  ProcedureCallService.create = function(dao, dataSource) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'object' && typeof __args[1] === 'object') {
      if (closed) {
        throw new Error('Proxy is closed');
      }
      j_eb.send(j_address, {"dao":__args[0], "dataSource":__args[1]}, {"action":"create"});
      return;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Creates a proxy that does all the boilerplate work of setting up access to <code>ProcedureCallService</code> via
   the Vertx Event Bus. You never need to call this method directly, Vertx will handle it for you but
   it's required as part of the Vertx Event Service Proxy contract.

   @memberof module:procedurecall-service-js/procedure_call_service
   @param vertx {Vertx} 
   @param address {string} 
   @return {todo} 
   */
  ProcedureCallService.createProxy = function(vertx, address) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && typeof __args[1] === 'string') {
      if (closed) {
        throw new Error('Proxy is closed');
      }
      j_eb.send(j_address, {"vertx":__args[0], "address":__args[1]}, {"action":"createProxy"});
      return;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  if (typeof exports !== 'undefined') {
    if (typeof module !== 'undefined' && module.exports) {
      exports = module.exports = ProcedureCallService;
    } else {
      exports.ProcedureCallService = ProcedureCallService;
    }
  } else {
    return ProcedureCallService;
  }
});
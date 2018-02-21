/**
 * @file   PMGDQueryHandler.h
 *
 * @section LICENSE
 *
 * The MIT License
 *
 * @copyright Copyright (c) 2017 Intel Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

#pragma once

#include <map>
#include <unordered_map>
#include <mutex>
#include <vector>
#include <list>

#include "protobuf/pmgdMessages.pb.h" // Protobuff implementation
#include "pmgd.h"
#include "SearchExpression.h"

namespace VDMS {
    // Instance created per worker thread to handle all transactions on a given
    // connection.

    typedef PMGD::protobufs::Command           PMGDCmd;
    typedef PMGD::protobufs::CommandResponse   PMGDCmdResponse;
    typedef PMGD::protobufs::PropertyPredicate PMGDPropPred;
    typedef PMGD::protobufs::PropertyList      PMGDPropList;
    typedef PMGD::protobufs::Property          PMGDProp;
    typedef PMGD::protobufs::QueryNode         PMGDQueryNode;

    typedef std::vector<PMGDCmd *>             PMGDCmds;
    typedef std::vector<PMGDCmdResponse *>     PMGDCmdResponses;

    class PMGDQueryHandler
    {
        class ReusableNodeIterator
        {
            // Iterator for the starting nodes.
            PMGD::NodeIterator _ni;

            // TODO Is list the best data structure if we could potentially
            // sort?
            std::list<PMGD::Node *> _traversed;

            // Current postion of list iterator
            std::list<PMGD::Node *>::iterator _it;

            bool _next()
            {
                if (_it != _traversed.end()) {
                    ++_it;
                    if (_it != _traversed.end())
                        return true;
                }
                if (bool(_ni)) {
                    _it = _traversed.insert(_traversed.end(), &*_ni);
                    _ni.next();
                    return true;
                }
                return false;
            }

            PMGD::Node *ref()
            {
                if (!bool(*this))
                    throw PMGDException(NullIterator, "Null impl");
                return *_it;
            }

            // TODO Is this the best way to do this
            struct compare_propkey
            {
                PMGD::StringID _propid;
                bool operator()(const PMGD::Node *n1, const PMGD::Node *n2)
                {
                   // if (n1 == NULL || n2 == NULL)
                     //   throw PMGDException(NullIterator, "Comparing at least one null node");
                    return n1->get_property(_propid) < n2->get_property(_propid);
                }
            };

        public:
            // Make sure this is not auto-declared. The move one won't be.
            ReusableNodeIterator(const ReusableNodeIterator &) = delete;
            ReusableNodeIterator(PMGD::NodeIterator ni)
                : _ni(ni),
                  _it(_traversed.begin())
            {
                _next();
            }

            // Add this to clean up the NewNodeIterator requirement
            ReusableNodeIterator(PMGD::Node *n)
                : _ni(NULL),
                  _it(_traversed.insert(_traversed.end(), n))
              {}

            operator bool() const { return _it != _traversed.end(); }
            bool next() { return _next(); }
            PMGD::Node &operator *() { return *ref(); }
            PMGD::Node *operator ->() { return ref(); }
            void reset() { _it = _traversed.begin(); }
            void traverse_all()
            {
                for( ; _ni; _ni.next())
                    _traversed.insert(_traversed.end(), &*_ni);
            }

            // Sort the list. Once the list is sorted, all operations
            // following that happen in a sorted manner. And this function
            // resets the iterator to the beginning.
            // TODO It might be possible to avoid this if the first iterator
            // was build out of an index sorted on the same key been sought here.
            // Hopefully that won't happen.
            void sort(PMGD::StringID sortkey)
            {
                // First finish traversal
                traverse_all();
                _traversed.sort(compare_propkey{sortkey});
                _it = _traversed.begin();
            }
        };

        class MultiNeighborIteratorImpl : public PMGD::NodeIteratorImplIntf
        {
            // Iterator for the starting nodes.
            ReusableNodeIterator *_start_ni;
            SearchExpression _search_neighbors;
            PMGD::NodeIterator *_neighb_i;
            PMGD::Direction _dir;
            PMGD::StringID _edge_tag;

            bool _next()
            {
                while (_start_ni != NULL && bool(*_start_ni)) {
                    delete _neighb_i;

                    // TODO Maybe unique can have a default value of false.
                    // TODO No support in case unique is true but get it from LDBC.
                    // Eventually need to add a get_union(NodeIterator, vector<Constraints>)
                    // call to PMGD.
                    // TODO Any way to skip new?
                    _neighb_i = new PMGD::NodeIterator(_search_neighbors.eval_nodes(**_start_ni,
                                               _dir, _edge_tag));
                    _start_ni->next();
                    if (bool(*_neighb_i))
                        return true;
                }
                _start_ni = NULL;
                return false;
            }

        public:
            MultiNeighborIteratorImpl(ReusableNodeIterator *start_ni,
                                      SearchExpression search_neighbors,
                                      PMGD::Direction dir,
                                      PMGD::StringID edge_tag)
                : _start_ni(start_ni),
                  _search_neighbors(search_neighbors),
                  _neighb_i(NULL),
                  _dir(dir),
                  _edge_tag(edge_tag)
            {
                _next();
            }

            ~MultiNeighborIteratorImpl()
            {
                delete _neighb_i;
            }

            operator bool() const { return _neighb_i != NULL ? bool(*_neighb_i) : false; }

            /// No next matching node
            bool next()
            {
                if (_neighb_i != NULL && bool(*_neighb_i)) {
                    _neighb_i->next();
                    if (bool(*_neighb_i))
                        return true;
                }
                return _next();
            }

            PMGD::Node *ref() { return &**_neighb_i; }
        };

        // This class is instantiated by the server each time there is a new
        // connection. So someone needs to pass a handle to the graph db itself.
        PMGD::Graph *_db;

        // Need this lock till we have concurrency support in JL
        // TODO: Make this reader writer.
        std::mutex *_dblock;

        PMGD::Transaction *_tx;

        // Map an integer ID to a NodeIterator (reset at the end of each transaction).
        // This works for Adds and Queries. We assume that the client or
        // the request server code will always add a ref to the AddNode
        // call or a query call. That is what is the key in the map.
        // Add calls typically don't result in a NodeIterator. But we make
        // one to keep the code uniform. In a chained query, there is no way
        // of finding out if the reference is for an AddNode or a QueryNode
        // and rather than searching multiple maps, we keep it uniform here.
        std::unordered_map<int, ReusableNodeIterator *> _cached_nodes;

        void process_query(const PMGDCmd *cmd, PMGDCmdResponse *response);
        void add_node(const PMGD::protobufs::AddNode &cn, PMGDCmdResponse *response);
        void add_edge(const PMGD::protobufs::AddEdge &ce, PMGDCmdResponse *response);
        template <class Element> void set_property(Element &e, const PMGDProp&p);
        void query_node(const PMGDQueryNode &qn, PMGDCmdResponse *response);
        PMGD::PropertyPredicate construct_search_term(const PMGDPropPred &p_pp);
        PMGD::Property construct_search_property(const PMGDProp&p);
        template <class Iterator> void build_results(Iterator &ni,
                                                    const PMGDQueryNode &qn,
                                                    PMGDCmdResponse *response);
        void construct_protobuf_property(const PMGD::Property &j_p, PMGDProp*p_p);

    public:
        PMGDQueryHandler(PMGD::Graph *db, std::mutex *mtx);

        // The vector here can contain just one JL command but will be surrounded by
        // TX begin and end. So just expose one call to the QueryHandler for
        // the request server.
        // The return vector contains an ordered list of query id with
        // group of commands that correspond to that query.
        // Pass the number of queries here since that could be different
        // than the number of commands.
        // Ensure that the cmd_grp_id, that is the query number are in increasing
        // order and account for the TxBegin and TxEnd in numbering.
        std::vector<PMGDCmdResponses> process_queries(const PMGDCmds &cmds, int num_groups);
    };
};

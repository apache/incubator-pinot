/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import React, {Component} from 'react';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import TypoGraphy from '@material-ui/core/Typography'
import {
    withRouter
} from 'react-router-dom'

class NavBar extends Component {

    render() {
        return (
            <List component="nav">
                <ListItem component="div" >
                    <ListItemText inset onClick={() => this.props.history.push('/cluster')}>
                        <TypoGraphy color="inherit" variant="title" >
                            Cluster
                        </TypoGraphy>
                    </ListItemText>

                    <ListItemText inset onClick={() => this.props.history.push('/tenants')}>
                        <TypoGraphy color="inherit" variant="title">
                            Tenants
                        </TypoGraphy>
                    </ListItemText>

                    <ListItemText inset onClick={() => this.props.history.push('/tables')}>
                        <TypoGraphy color="inherit" variant="title">
                            Tables
                        </TypoGraphy>
                    </ListItemText>

                    <ListItemText inset onClick={() => this.props.history.push('/controllers')}>
                        <TypoGraphy color="inherit" variant="title">
                            Controllers
                        </TypoGraphy>
                    </ListItemText>

                    <ListItemText inset onClick={() => this.props.history.push('/servers')}>
                        <TypoGraphy color="inherit" variant="title">
                            Servers
                        </TypoGraphy>
                    </ListItemText>

                    <ListItemText inset onClick={() => this.props.history.push('/brokers')}>
                        <TypoGraphy color="inherit" variant="title">
                            Brokers
                        </TypoGraphy>
                    </ListItemText>
                </ListItem>
            </List>)
    }
}


export default withRouter(NavBar);
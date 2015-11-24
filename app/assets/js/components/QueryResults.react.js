import React from 'react';
import {History} from 'react-router';
import UserRecommendation from './UserRecommendation.react';
import Hashtag from './Hashtag.react';
import FAIcon from './FAIcon.react';
import {Row, Col} from 'elemental';
import {IconButton, ListDivider, CircularProgress, Paper, FlatButton} from 'material-ui';

const QueryResults = React.createClass({

    mixins: [History],

    getInitialState: function() {
        return {
            querySocket: null,
            queryResults: [],
            queryComplete: false
        }
    },

    componentWillMount: function() {
        // Create the WebSocket
        console.log('Mounting QueryResults');
        let querySocket = new WebSocket(`ws://localhost:9000/ws/${this.props.params.query}`)
        querySocket.onmessage = event => {
            this.setState({queryComplete: true, queryResults: JSON.parse(event.data)})
        };
        if (this.state.querySocket != null) {
            querySocket.close();
        }
        this.setState({
            querySocket: querySocket
        });
    },

    componentWillUnmount: function() {
        console.log("Dismounting QueryResults");
        if (this.state.querySocket != null) {
            this.state.querySocket.close();
        }
    },

    onClickBackButton: function(e) {
        this.history.goBack();
    },

    render: function() {
        let queryResults = [];
        this.state.queryResults.forEach((result, idx) => {
            queryResults.push(
                <UserRecommendation key={`recommendation:${idx}`}
                                    username={result.username}
                                    rating={result.rating}
                                    query={result.query} />
            );
            if (idx !== this.state.queryResults.length -1) {
                queryResults.push(<ListDivider key={`divider:${idx}`}/>)
            }
        }, this);
        let spinner = <CircularProgress mode="indeterminate" />;
        let noResultsMessage = <Paper zDepth={1}>Your query returned no results.</Paper>;
        return (
            <Row>
                <Col sm="20%">
                    <IconButton iconClassName="material-icons" tooltipPosition="bottom-center"
                                tooltip="Search again" onClick={this.onClickBackButton}>arrow_back</IconButton>
                </Col>
                <Col sm="80%">
                    <h1>Recommendations for <Hashtag hashtag={this.props.params.query}/></h1>
                </Col>
                <Col sm="100%">
                    {queryResults.length > 0 ?
                        queryResults :
                        (this.state.queryComplete ?
                            noResultsMessage :
                            spinner)}

                </Col>
            </Row>
        )
    }

});
module.exports = QueryResults;
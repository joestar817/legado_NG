#!/usr/bin/env python3
"""Opt-in device integration suite. Creates unique fixtures and cleans them in finally.

Requires adb forward tcp:14324 tcp:1124 and reverse tcp:18761 tcp:18761.
Runs a loopback fixture website so the Android app uses its real HTTP/rule/Room pipeline.
"""
import argparse
import copy
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time
import traceback
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen
import uuid


class Suite:
    def __init__(self, endpoint, output, port):
        self.endpoint, self.output, self.port = endpoint, Path(output), port
        self.output.mkdir(parents=True, exist_ok=True)
        self.tag = "mcp-e2e-" + uuid.uuid4().hex[:12]
        self.base = f"http://127.0.0.1:{port}/{self.tag}"
        self.calls, self.checks, self.payloads, self.http = [], [], {}, []
        self.rss, self.books, self.stars, self.records, self.subs = [], [], [], [], []
        self.counter = 0
        suite = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_GET(self):
                parsed = urlsplit(self.path)
                suite.http.append(self.path)
                route = parsed.path.removeprefix('/' + suite.tag)
                query = parse_qs(parsed.query)
                page = int(query.get('page', ['1'])[0])
                status, mime = 200, 'application/json; charset=utf-8'
                if route in suite.payloads:
                    body = suite.payloads[route]
                elif route == '/slow':
                    time.sleep(3)
                    body = {'items': [], 'next': ''}
                elif route == '/failure':
                    status, body = 500, 'fixture failure'
                elif route == '/feed':
                    mime = 'application/rss+xml; charset=utf-8'
                    body = f'<rss version="2.0"><channel><title>Fixture</title><item><title>原生RSS</title><link>{suite.base}/article/rss</link><description>默认解析正文</description></item></channel></rss>'
                elif route in ('/list', '/empty', '/books'):
                    items = [] if route == '/empty' or page > 2 else [
                        {'title': f'文章{page}-{i}', 'name': f'图书{page}-{i}', 'author': '测试作者',
                         'link': f'{suite.base}/article/{page}-{i}', 'bookUrl': f'{suite.base}/book/{page}-{i}',
                         'description': f'摘要{page}-{i}'} for i in range(1, 4)]
                    body = {'items': items, 'next': f'{suite.base}/list?page=2' if page == 1 else ''}
                elif route.startswith('/article/'):
                    body = {'text': '完整正文😀第二段\n结束', 'title': '文章详情'}
                elif route.startswith('/book/'):
                    body = {'name': '原生详情书名', 'author': '测试作者', 'intro': '原生详情介绍', 'toc': suite.base + '/toc'}
                elif route == '/toc':
                    body = {'chapters': [{'title': '第一章', 'url': suite.base + '/article/chapter'}]}
                else:
                    status, body = 404, 'not found'
                encoded = (body if isinstance(body, str) else json.dumps(body, ensure_ascii=False)).encode()
                self.send_response(status)
                self.send_header('Content-Type', mime)
                self.send_header('Content-Length', str(len(encoded)))
                self.end_headers()
                try:
                    self.wfile.write(encoded)
                except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                    pass

        self.server = ThreadingHTTPServer(('127.0.0.1', port), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def rpc(self, method, params=None):
        self.output.joinpath('fixtures.json').write_text(json.dumps({
            'tag': self.tag, 'rss': self.rss, 'books': self.books, 'stars': self.stars,
            'records': self.records, 'subscriptions': self.subs,
            'replace_rule': getattr(self, 'replace_rule', None)
        }, ensure_ascii=False, indent=2), encoding='utf-8')
        request = {'jsonrpc': '2.0', 'id': len(self.calls) + 1, 'method': method, 'params': params or {}}
        started = time.monotonic()
        with urlopen(Request(self.endpoint, json.dumps(request, ensure_ascii=False).encode(),
                            {'Content-Type': 'application/json'}), timeout=150) as response:
            result = json.load(response)
        self.calls.append({'method': method, 'params': params, 'response': result,
                           'elapsed_ms': round((time.monotonic() - started) * 1000)})
        return result

    def call(self, name, arguments=None, ok=True):
        envelope = self.rpc('tools/call', {'name': name, 'arguments': arguments or {}})
        assert 'error' not in envelope, (name, envelope)
        result = envelope['result']
        assert result['isError'] == (not ok), (name, result)
        data = result['structuredContent']
        assert data['ok'] == ok, (name, data)
        return data.get('normalized_data')

    def check(self, name, function):
        try:
            function()
            self.checks.append({'name': name, 'passed': True})
            print('PASS', name, flush=True)
        except Exception:
            error = traceback.format_exc()
            self.checks.append({'name': name, 'passed': False, 'error': error})
            print('FAIL', name, error[-1600:], flush=True)

    @staticmethod
    def equal(actual, expected):
        assert actual == expected, (actual, expected)

    def new_rss(self, **overrides):
        self.counter += 1
        url = f'{self.base}/source/{self.counter}'
        source = {'sourceUrl': url, 'sourceName': self.tag + '-' + str(self.counter),
                  'sourceGroup': self.tag, 'enabled': False, 'customOrder': 98765,
                  'sortUrl': f'分类::{self.base}/list?page={{{{page}}}}',
                  'searchUrl': f'{self.base}/list?page={{{{page}}}}&q={{{{key}}}}',
                  'ruleArticles': '$.items', 'ruleTitle': '$.title', 'ruleLink': '$.link',
                  'ruleNextPage': '$.next', 'ruleContent': '$.text'}
        source.update(overrides)
        self.rss.append(source['sourceUrl'])
        self.call('rss_source_save', {'source': source})
        return source

    def get(self, source):
        return self.call('rss_source_get', {'source_url': source['sourceUrl']})

    def fetch(self, source, **kwargs):
        return self.call('rss_articles_fetch', {'source_url': source['sourceUrl'], **kwargs})

    def all_pages(self, tool, key, **arguments):
        items, offset = [], 0
        while True:
            data = self.call(tool, dict(arguments, offset=offset, limit=200))
            items.extend(data[key])
            offset = data.get('next_offset')
            if offset is None:
                return items

    def snapshot(self):
        return {
            'sources': self.all_pages('rss_source_list', 'sources', include_detail=True),
            'stars': self.all_pages('rss_star_list', 'stars'),
            'records': self.all_pages('rss_read_record_list', 'records'),
            'subscriptions': self.all_pages('rss_rule_subscription_list', 'subscriptions'),
            'replace_rules': self.all_pages('settings_replace_rule_list', 'rules', include_detail=True),
            'book_sources': self.call('book_source_stats_get'),
        }

    def run(self):
        self.before = self.snapshot()
        self.output.joinpath('before.json').write_text(json.dumps(self.before, ensure_ascii=False, indent=2), encoding='utf-8')
        try:
            self.check('source partial edit and exact export', self.source_edit)
            self.check('source import skip overwrite and atomic validation', self.source_import)
            self.check('source enable and delete atomic identity checks', self.source_manage)
            self.check('source identifiers preserve whitespace', self.identity)
            self.check('source all editable fields roundtrip', self.source_fields)
            self.check('categories static dynamic refresh and errors', self.categories)
            self.check('RSS remote page continuation and local pagination', self.pagination)
            self.check('RSS PAGE rule and search', self.search)
            self.check('RSS default parser and empty results', self.default_feed)
            self.check('RSS content windows and WebView boundary', self.content)
            self.check('favorites create update read and delete', self.favorites)
            self.check('history create update collision and delete', self.history)
            self.check('article malformed payloads rejected', self.invalid_articles)
            self.check('native RSS debug success failure override timeout', self.debug)
            self.check('concurrent native debug rejects second owner', self.concurrent_debug)
            self.check('network timeout error and recovery', self.network_errors)
            self.check('discovery categories pages details and enable independence', self.discovery)
            self.check('legacy source search debug and shared lock recovery', self.legacy_source)
            for kind in (0, 1, 2):
                self.check(f'rule subscription type {kind} CRUD preview interval silent update', lambda k=kind: self.subscription(k))
            self.check('all new tool parameter rejection', self.invalid_arguments)
        finally:
            self.cleanup()
            self.after = self.snapshot()
            self.check('original data unchanged after fixture cleanup', lambda: self.equal(self.after, self.before))
            self.server.shutdown()
            self.output.joinpath('after.json').write_text(json.dumps(self.after, ensure_ascii=False, indent=2), encoding='utf-8')
            self.output.joinpath('rpc.json').write_text(json.dumps(self.calls, ensure_ascii=False, indent=2), encoding='utf-8')
            self.output.joinpath('http.json').write_text(json.dumps(self.http, ensure_ascii=False, indent=2), encoding='utf-8')
            summary = {'tag': self.tag, 'checks': self.checks, 'rpc_count': len(self.calls),
                       'tools_called': sorted({c['params']['name'] for c in self.calls if c['method'] == 'tools/call'})}
            self.output.joinpath('summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
        return all(c['passed'] for c in self.checks)

    def source_edit(self):
        source = self.new_rss(jsLib='var x=1;', sourceComment='保留字段')
        before = self.get(source)
        self.call('rss_source_save', {'source': {'sourceUrl': source['sourceUrl'], 'sourceName': '新名', 'ruleContent': None}})
        after = self.get(source)
        for key, value in before.items():
            if key not in ('lastUpdateTime', 'sourceName', 'ruleContent'):
                self.equal(after.get(key), value)
        assert not after.get('ruleContent')
        self.equal(self.call('rss_source_export', {'urls': [source['sourceUrl']]})['sources'], [after])
        for patch in ({'enabled': 'false'}, {'sourceName': None}, {'customOrder': 1.5}, {'type': 8}, {'typo': 1}):
            self.call('rss_source_save', {'source': dict(patch, sourceUrl=source['sourceUrl'])}, ok=False)
        self.equal(self.get(source), after)

    def source_import(self):
        source = self.new_rss()
        before = self.get(source)
        patch = dict(before, sourceName='Imported', customOrder=123)
        self.equal(self.call('rss_source_import', {'sources': [patch]})['imported'], 0)
        self.equal(self.get(source), before)
        self.equal(self.call('rss_source_import', {'sources': [patch], 'overwrite': True})['imported'], 1)
        self.equal(self.get(source)['sourceName'], 'Imported')
        self.equal(self.get(source)['customOrder'], 98765)
        fresh = dict(source, sourceUrl=source['sourceUrl'] + '/new')
        self.rss.append(fresh['sourceUrl'])
        self.call('rss_source_import', {'sources': [fresh, {'sourceUrl': 'invalid'}]}, ok=False)
        self.call('rss_source_get', {'source_url': fresh['sourceUrl']}, ok=False)
        self.call('rss_source_import', {'sources': [fresh, fresh]}, ok=False)
        self.equal(self.call('rss_source_import', {'sources': [fresh]})['imported'], 1)

    def source_manage(self):
        source = self.new_rss()
        url = source['sourceUrl']
        self.equal(self.call('rss_source_stats_get')['total'], len(self.all_pages('rss_source_list', 'sources')))
        for tool in ('rss_source_set_enabled', 'rss_source_delete'):
            arguments = {'urls': [url, url + '/missing']}
            if tool.endswith('enabled'):
                arguments['enabled'] = True
            self.call(tool, arguments, ok=False)
            assert self.get(source)['enabled'] is False
        self.call('rss_source_set_enabled', {'urls': [url], 'enabled': True})
        assert self.get(source)['enabled'] is True
        filtered = self.call('rss_source_list', {'group': self.tag, 'enabled': True})
        assert url in [s['sourceUrl'] for s in filtered['sources']]
        self.call('rss_source_delete', {'urls': [url]})
        self.call('rss_source_get', {'source_url': url}, ok=False)

    def identity(self):
        source = self.new_rss(sourceUrl=' ' + self.base + '/space ')
        self.equal(self.get(source)['sourceUrl'], source['sourceUrl'])
        self.call('rss_source_get', {'source_url': source['sourceUrl'].strip()}, ok=False)

    def source_fields(self):
        resource = self.rpc('resources/read', {'uri': 'legado://schema/discovery-subscription'})['result']
        fields = json.loads(resource['contents'][0]['text'])['rss_source_fields']
        source = self.new_rss()
        values = {key: ('样本' if kind == 'string' else False if kind == 'boolean' else 0) for key, kind in fields.items()}
        values['sourceUrl'] = source['sourceUrl']
        self.call('rss_source_save', {'source': values})
        saved = self.get(source)
        for key, value in values.items():
            if key != 'lastUpdateTime':
                self.equal(saved.get(key), value)

    def categories(self):
        source = self.new_rss()
        data = self.call('rss_source_categories', {'source_url': source['sourceUrl']})
        self.equal(data['categories'][0]['name'], '分类')
        self.call('rss_source_save', {'source': {'sourceUrl': source['sourceUrl'], 'sortUrl': '@js:"动态::' + self.base + '/list"'}})
        self.equal(self.call('rss_source_categories', {'source_url': source['sourceUrl'], 'refresh': True})['categories'][0]['name'], '动态')
        self.call('rss_source_save', {'source': {'sourceUrl': source['sourceUrl'], 'sortUrl': '@js:throw new Error("fixture category error")'}})
        self.call('rss_source_categories', {'source_url': source['sourceUrl'], 'refresh': True}, ok=False)

    def pagination(self):
        source = self.new_rss()
        first = self.fetch(source, limit=1)
        self.equal(first['total'], 3)
        self.equal(first['next_offset'], 1)
        self.equal(self.fetch(source, offset=1, limit=1)['articles'][0]['title'], '文章1-2')
        second = self.fetch(source, sort=first['sort'], sort_url=first['next_page_url'], page=2)
        self.equal(second['articles'][0]['title'], '文章2-1')
        assert not second['has_more']
        self.call('rss_articles_fetch', {'source_url': source['sourceUrl'], 'page': 2}, ok=False)
        self.equal(self.call('rss_article_list', {'source_url': source['sourceUrl'], 'sort': '分类'})['total'], 0)
        self.call('rss_article_get', {'source_url': source['sourceUrl'], 'sort': '分类', 'link': first['articles'][0]['link']}, ok=False)

    def search(self):
        source = self.new_rss(ruleNextPage='PAGE')
        first = self.fetch(source, key='中文 测试')
        second = self.fetch(source, key='中文 测试', sort=first['sort'], sort_url=first['next_page_url'], page=2)
        self.equal(second['articles'][0]['title'], '文章2-1')
        self.equal(first['sort'], '搜索')
        self.call('rss_source_save', {'source': {'sourceUrl': source['sourceUrl'], 'searchUrl': None}})
        self.call('rss_articles_fetch', {'source_url': source['sourceUrl'], 'key': 'none'}, ok=False)

    def default_feed(self):
        source = self.new_rss(sortUrl='默认::' + self.base + '/feed', ruleArticles=None, ruleContent=None)
        result = self.fetch(source)
        self.equal(result['articles'][0]['title'], '原生RSS')
        assert not result['has_more']
        empty = self.new_rss(sortUrl='空::' + self.base + '/empty')
        self.equal(self.fetch(empty)['total'], 0)

    def article(self, source):
        return self.fetch(source)['articles'][0]

    def content(self):
        source = self.new_rss()
        article = self.article(source)
        arguments = {'source_url': source['sourceUrl'], 'article': article}
        body = self.call('rss_article_content_get', arguments)['content']
        self.equal(body, '完整正文😀第二段\n结束')
        for limit in (1, 5):
            offset, parts = 0, []
            while True:
                window = self.call('rss_article_content_get', dict(arguments, article=dict(article, description=body), offset=offset, max_chars=limit))
                parts.append(window['content'])
                if window.get('next_offset') is None:
                    break
                assert window['next_offset'] > offset
                offset = window['next_offset']
            self.equal(''.join(parts), body)
        self.equal(self.call('rss_article_content_get', dict(arguments, offset=0, max_chars=4))['content'], '完整正文')
        self.equal(self.call('rss_article_content_get', dict(arguments, offset=999))['content'], '')
        cached = dict(article, description='已有正文')
        self.equal(self.call('rss_article_content_get', dict(arguments, article=cached))['content'], '已有正文')
        self.equal(self.call('rss_article_content_get', dict(arguments, article=cached, refresh=True))['content'], body)
        self.call('rss_source_save', {'source': {'sourceUrl': source['sourceUrl'], 'ruleContent': None}})
        assert self.call('rss_article_content_get', arguments)['requires_ui']
        single = self.new_rss(singleUrl=True)
        self.call('rss_articles_fetch', {'source_url': single['sourceUrl']}, ok=False)

    def favorites(self):
        source = self.new_rss()
        article = self.article(source)
        origin, link = source['sourceUrl'], article['link']
        self.stars.append((origin, link))
        saved = self.call('rss_star_save', {'source_url': origin, 'article': article, 'group': self.tag})
        self.equal(self.call('rss_star_get', {'source_url': origin, 'link': link}), saved)
        updated = self.call('rss_star_save', {'source_url': origin, 'link': link, 'sort': article['sort'], 'group': self.tag + '-2'})
        self.equal(updated['starTime'], saved['starTime'])
        self.equal(self.call('rss_star_list', {'source_url': origin, 'group': self.tag + '-2'})['total'], 1)
        self.equal(self.call('rss_article_get', {'source_url': origin, 'link': link, 'sort': article['sort']})['title'], article['title'])
        self.call('rss_source_delete', {'urls': [origin]})
        self.call('rss_star_get', {'source_url': origin, 'link': link})
        assert self.call('rss_star_delete', {'source_url': origin, 'link': link})['deleted']
        assert not self.call('rss_star_delete', {'source_url': origin, 'link': link})['deleted']

    def history(self):
        source = self.new_rss()
        article = self.article(source)
        origin, link = source['sourceUrl'], article['link']
        self.records.append((origin, link))
        self.call('rss_read_record_save', {'source_url': origin, 'article': article, 'position': 7})
        self.equal(self.call('rss_read_record_get', {'source_url': origin, 'link': link})['durPos'], 7)
        self.call('rss_read_record_save', {'source_url': origin, 'article': article, 'position': 12})
        self.equal(self.call('rss_read_record_list', {'source_url': origin})['records'][0]['durPos'], 12)
        other = self.new_rss()
        self.call('rss_read_record_save', {'source_url': other['sourceUrl'], 'article': dict(article, origin=other['sourceUrl'])}, ok=False)
        self.call('rss_read_record_save', {'source_url': origin, 'article': article, 'position': -1}, ok=False)
        self.equal(self.call('rss_read_record_delete', {'source_url': origin, 'link': link})['deleted'], 1)
        self.equal(self.call('rss_read_record_delete', {'source_url': origin, 'link': link})['deleted'], 0)

    def invalid_articles(self):
        source = self.new_rss()
        article = self.article(source)
        for patch in ({'origin': 'mismatch'}, {'link': ''}, {'title': None}, {'type': 99}, {'durPos': -1}, {'read': 'false'}):
            self.call('rss_article_content_get', {'source_url': source['sourceUrl'], 'article': dict(article, description='safe', **patch)}, ok=False)

    def debug(self):
        source = self.new_rss()
        arguments = {'source_url': source['sourceUrl'], 'timeout_seconds': 8}
        assert self.call('rss_source_debug', arguments)['done']
        self.call('rss_source_debug', dict(arguments, key='测试'))
        before = self.get(source)
        self.call('rss_source_debug', dict(arguments, key='分类::http://127.0.0.1:18762/unreachable'), ok=False)
        self.call('rss_source_debug', dict(arguments, key='分类::' + self.base + '/list', source_override={'ruleArticles': '@js:throw new Error("fixture")'}), ok=False)
        self.equal(self.get(source), before)
        self.call('rss_source_debug', dict(arguments, key='分类::' + self.base + '/slow', timeout_seconds=1), ok=False)
        self.call('rss_source_debug', arguments)

    def network_errors(self):
        source = self.new_rss()
        self.call('rss_articles_fetch', {'source_url': source['sourceUrl'], 'sort_url': 'http://127.0.0.1:18762/unreachable'}, ok=False)
        started = time.monotonic()
        self.call('rss_articles_fetch', {'source_url': source['sourceUrl'], 'sort_url': self.base + '/slow', 'timeout_seconds': 1}, ok=False)
        assert time.monotonic() - started < 6
        assert self.fetch(source)['articles']

    def concurrent_debug(self):
        source = self.new_rss()
        arguments = {'source_url': source['sourceUrl'], 'key': '慢::' + self.base + '/slow', 'timeout_seconds': 8}
        initial = self.http.count('/' + self.tag + '/slow')
        with ThreadPoolExecutor(max_workers=2) as executor:
            first = executor.submit(self.call, 'rss_source_debug', arguments)
            deadline = time.monotonic() + 5
            while self.http.count('/' + self.tag + '/slow') == initial and time.monotonic() < deadline:
                time.sleep(0.02)
            assert self.http.count('/' + self.tag + '/slow') > initial
            try:
                started = time.monotonic()
                self.call('rss_source_debug', {'source_url': source['sourceUrl'], 'timeout_seconds': 2}, ok=False)
                assert time.monotonic() - started < 1.5
            finally:
                first.result()

    def new_book_source(self):
        url = self.base + '/book-source/' + str(len(self.books))
        source = {'bookSourceUrl': url, 'bookSourceName': self.tag, 'bookSourceGroup': self.tag,
                  'enabled': False, 'enabledExplore': True, 'exploreUrl': '分类::' + self.base + '/books?page={{page}}',
                  'searchUrl': self.base + '/books?page={{page}}&key={{key}}',
                  'ruleSearch': {'bookList': '$.items', 'name': '$.name', 'author': '$.author', 'bookUrl': '$.bookUrl'},
                  'ruleExplore': {'bookList': '$.items', 'name': '$.name', 'author': '$.author', 'bookUrl': '$.bookUrl'},
                  'ruleBookInfo': {'name': '$.name', 'author': '$.author', 'intro': '$.intro', 'tocUrl': '$.toc'},
                  'ruleToc': {'chapterList': '$.chapters', 'chapterName': '$.title', 'chapterUrl': '$.url'},
                  'ruleContent': {'content': '$.text'}}
        self.books.append(url)
        self.call('book_source_save', {'source': source})
        return source

    def discovery(self):
        source = self.new_book_source()
        url = source['bookSourceUrl']
        self.equal(self.call('explore_source_list', {'group': self.tag})['total'], 1)
        self.call('book_source_explore_kinds_get', {'url': url, 'refresh': True})
        self.call('explore_source_set_enabled', {'urls': [url], 'enabled': False})
        saved = self.call('book_source_get', {'url': url})
        assert saved['enabled'] is False and saved['enabledExplore'] is False
        self.call('explore_source_set_enabled', {'urls': [url, url + '/missing'], 'enabled': True}, ok=False)
        assert self.call('book_source_get', {'url': url})['enabledExplore'] is False
        self.call('explore_source_set_enabled', {'urls': [url], 'enabled': True})
        first = self.call('explore_books', {'source_url': url, 'category_index': 0, 'limit': 1})
        self.equal(first['total'], 3)
        self.equal(self.call('explore_book_info', {'source_url': url, 'book_url': first['books'][0]['book_url']})['name'], '原生详情书名')
        self.equal(self.call('explore_books', {'source_url': url, 'page': 2})['books'][0]['name'], '图书2-1')
        assert self.call('explore_books', {'source_url': url, 'page': 3})['end_confirmed']
        self.call('explore_books', {'source_url': url, 'category_index': 999}, ok=False)
        self.call('explore_books', {'source_url': url, 'category_index': 0, 'explore_url': 'x'}, ok=False)

    def subscription(self, kind):
        path = f'/subscription/{kind}.json'
        url = self.base + path
        self.subs.append(url)
        if kind == 0:
            source = self.new_book_source()
            source['lastUpdateTime'] = int(time.time() * 1000) + 60000
            self.payloads[path] = [dict(source, bookSourceName='updated fixture book')]
        elif kind == 1:
            source = self.new_rss()
            source['lastUpdateTime'] = int(time.time() * 1000) + 60000
            self.payloads[path] = [dict(source, sourceName='updated fixture rss')]
        else:
            rule_id = int(time.time() * 1000)
            self.replace_rule = rule_id
            self.payloads[path] = [{'id': rule_id, 'name': self.tag, 'pattern': 'fixture-pattern', 'replacement': 'fixture-replacement', 'isEnabled': False}]
        self.call('rss_rule_subscription_save', {'subscription': {'url': url, 'name': self.tag, 'type': kind, 'autoUpdate': False, 'updateInterval': 24}})
        before = self.call('rss_rule_subscription_get', {'url': url})
        data = self.call('rss_rule_subscription_refresh', {'url': url})
        assert not data['checked']
        preview = self.call('rss_rule_subscription_refresh', {'url': url, 'force': True})
        assert preview['preview_available'] and len(preview['pending_rules']) == 1
        self.call('rss_rule_subscription_save', {'subscription': {'url': url, 'silentUpdate': True, 'name': 'patched'}})
        self.equal(self.call('rss_rule_subscription_get', {'url': url})['id'], before['id'])
        updated = self.call('rss_rule_subscription_refresh', {'url': url, 'force': True})
        assert not updated['preview_available']
        if kind == 0:
            self.equal(self.call('book_source_get', {'url': source['bookSourceUrl']})['bookSourceName'], 'updated fixture book')
        elif kind == 1:
            self.equal(self.get(source)['sourceName'], 'updated fixture rss')
        else:
            self.call('settings_replace_rule_get', {'id': rule_id})
        self.call('rss_rule_subscription_save', {'subscription': {'url': url, 'id': 1}}, ok=False)
        self.call('rss_rule_subscription_delete', {'url': url})
        self.call('rss_rule_subscription_get', {'url': url}, ok=False)

    def legacy_source(self):
        book = self.new_book_source()
        url = book['bookSourceUrl']
        result = self.call('book_search', {'key': '图书', 'scope': self.tag + '::' + url,
                           'wait_for_finish': True, 'timeout_seconds': 8, 'limit': 3})
        assert result['done'] and result['source_count'] == 1
        self.equal(len(result['books']), 3)
        debug_args = {'tag': url, 'mode': 'explore', 'key': self.base + '/books', 'timeout_seconds': 8}
        debug = self.call('book_source_debug', debug_args)
        assert debug['done'] and debug['logs']
        assert not any('Exception' in line or 'Error:' in line for line in debug['logs']), debug['logs']
        rss = self.new_rss()
        initial = self.http.count('/' + self.tag + '/slow')
        with ThreadPoolExecutor(max_workers=2) as executor:
            first = executor.submit(self.call, 'rss_source_debug', {'source_url': rss['sourceUrl'],
                                    'key': '慢::' + self.base + '/slow', 'timeout_seconds': 8})
            deadline = time.monotonic() + 5
            while self.http.count('/' + self.tag + '/slow') == initial and time.monotonic() < deadline:
                time.sleep(0.02)
            assert self.http.count('/' + self.tag + '/slow') > initial
            try:
                started = time.monotonic()
                rejected = self.rpc('tools/call', {'name': 'book_source_debug', 'arguments': debug_args})
                assert 'error' in rejected
                assert time.monotonic() - started < 1.5
            finally:
                first.result()
        assert self.call('book_source_debug', debug_args)['done']

    def invalid_arguments(self):
        resource = self.rpc('resources/read', {'uri': 'legado://schema/discovery-subscription'})['result']
        tools = json.loads(resource['contents'][0]['text'])['tools']
        for tool in tools:
            self.call(tool['name'], {'unexpected': True}, ok=False)

    def cleanup(self):
        def attempt(name, args):
            try:
                self.call(name, args)
            except Exception as error:
                self.checks.append({'name': 'cleanup ' + name, 'passed': False, 'error': str(error)})
        for url in self.subs:
            attempt('rss_rule_subscription_delete', {'url': url})
        for origin, link in self.stars:
            attempt('rss_star_delete', {'source_url': origin, 'link': link})
        for origin, link in self.records:
            attempt('rss_read_record_delete', {'source_url': origin, 'link': link})
        for url in self.rss:
            # Failed imports and explicit delete tests may already have removed this exact fixture.
            response = self.rpc('tools/call', {'name': 'rss_source_get', 'arguments': {'source_url': url}})
            if response.get('result', {}).get('structuredContent', {}).get('ok'):
                attempt('rss_source_delete', {'urls': [url]})
        if self.books:
            attempt('book_source_delete', {'urls': self.books})
        if hasattr(self, 'replace_rule'):
            attempt('settings_replace_rule_delete', {'ids': [self.replace_rule]})


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='backslashreplace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--endpoint', default='http://127.0.0.1:14324/mcp')
    parser.add_argument('--port', type=int, default=18761)
    parser.add_argument('--output', required=True)
    parser.add_argument('--write-fixtures', action='store_true', required=True)
    args = parser.parse_args()
    suite = Suite(args.endpoint, args.output, args.port)
    raise SystemExit(0 if suite.run() else 1)


if __name__ == '__main__':
    main()

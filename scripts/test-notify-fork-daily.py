#!/usr/bin/env python3
"""Exercise notification outcomes and delivery safeguards without Discord writes."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('notify', Path(__file__).with_name('notify-fork-daily.py'))
notify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notify)
RECIPIENTS = ['111111111111111111', '222222222222222222']
CHANNEL = '333333333333333333'
WEBHOOK = 'https://discord.com/api/webhooks/444444444444444444/test-token'


def event(action='completed', conclusion='success'):
    return {'action': action, 'workflow_run': {
        'id': 42, 'run_number': 7, 'run_attempt': 2, 'conclusion': conclusion,
        'html_url': 'https://github.com/SiloraOSS/silora-android/actions/runs/42',
    }}


def jobs(sync='success', build='success', publish='success'):
    return [{'name': name, 'conclusion': result} for name, result in
            (('sync', sync), ('build', build), ('publish', publish))]


class NotificationTest(unittest.TestCase):
    def test_only_failed_outcomes_ping_exact_recipients(self):
        for conclusion in ('failure', 'timed_out', 'action_required', 'startup_failure'):
            with self.subTest(conclusion=conclusion):
                payload = notify.notification(event(conclusion=conclusion), [], RECIPIENTS)
                self.assertEqual(payload['content'], ' '.join(f'<@{uid}>' for uid in RECIPIENTS))
                self.assertEqual(payload['allowed_mentions']['users'], RECIPIENTS)
                self.assertEqual(payload['allowed_mentions']['parse'], [])
                self.assertEqual(payload['allowed_mentions']['roles'], [])
        for action, conclusion in (('in_progress', None), ('completed', 'success'),
                                   ('completed', 'cancelled'), ('completed', 'skipped'), ('completed', 'neutral')):
            with self.subTest(action=action, conclusion=conclusion):
                payload = notify.notification(event(action, conclusion), [], RECIPIENTS)
                self.assertEqual(payload['content'], '')
                self.assertEqual(payload['allowed_mentions']['users'], [])

    def test_no_changes_is_distinct_from_published_release(self):
        unchanged = notify.notification(event(), jobs(build='skipped', publish='skipped'), RECIPIENTS)
        self.assertIn('no changes', unchanged['embeds'][0]['title'])
        published = notify.notification(event(), jobs(), RECIPIENTS)
        self.assertIn('publication succeeded', published['embeds'][0]['description'])
        unknown = notify.notification(event(), [], RECIPIENTS)
        self.assertNotIn('publication succeeded', unknown['embeds'][0]['description'])

    def test_merge_failure_survives_skipped_build_and_publish(self):
        failed = jobs(sync='failure', build='skipped', publish='skipped')
        failed[0]['steps'] = [{'name': 'Merge upstream into main', 'conclusion': 'failure'}]
        payload = notify.notification(event(conclusion='failure'), failed, RECIPIENTS)
        self.assertIn('merge conflict or GitHub API error', payload['embeds'][0]['description'])
        self.assertIn('building and publishing were skipped', payload['embeds'][0]['description'])
        self.assertIn('Attempt 2', payload['embeds'][0]['footer']['text'])

    def test_failed_job_lookup_does_not_suppress_failure_alert(self):
        trigger = event(conclusion='failure')
        trigger['workflow_run'].update(name='Silora Daily', head_branch='main', event='schedule',
                                       head_repository={'full_name': 'SiloraOSS/silora-android'})
        with mock.patch.object(notify.Path, 'read_text', return_value=notify.json.dumps(trigger)), \
                mock.patch.dict(notify.os.environ, {'GITHUB_EVENT_PATH': 'event.json',
                                                   'DISCORD_FAILURE_USER_IDS': ','.join(RECIPIENTS)}), \
                mock.patch.object(notify, 'workflow_jobs', side_effect=notify.NotificationError('unavailable')), \
                mock.patch.object(notify, 'deliver', return_value='555555555555555555') as send:
            notify.main()
        self.assertEqual(send.call_args.args[2]['allowed_mentions']['users'], RECIPIENTS)

    def test_manual_notifications_report_builds_and_failures_without_claiming_a_sync(self):
        for action, conclusion in (('in_progress', None), ('completed', 'success'),
                                   ('completed', 'failure'), ('completed', 'cancelled')):
            trigger = event(action, conclusion)
            trigger['workflow_run']['name'] = 'Silora Manual Release'
            results = [{'name': name, 'conclusion': 'success'} for name in ('prepare', 'build', 'publish')]
            with self.subTest(action=action, conclusion=conclusion):
                payload = notify.notification(trigger, results, RECIPIENTS)
                embed = payload['embeds'][0]
                self.assertIn('Silora Manual Release', embed['title'])
                self.assertNotIn('Upstream sync,', embed['description'])
                self.assertEqual(payload['allowed_mentions']['users'], RECIPIENTS if conclusion == 'failure' else [])
                if conclusion == 'success':
                    self.assertIn('Build and publication succeeded.', embed['description'])

    def test_manual_dispatch_is_accepted_but_automated_manual_events_are_rejected(self):
        for source_event in ('workflow_dispatch', 'schedule'):
            trigger = event()
            trigger['workflow_run'].update(name='Silora Manual Release', head_branch='main', event=source_event,
                                           head_repository={'full_name': 'SiloraOSS/silora-android'})
            with self.subTest(source_event=source_event), \
                    mock.patch.object(notify.Path, 'read_text', return_value=notify.json.dumps(trigger)), \
                    mock.patch.dict(notify.os.environ, {'GITHUB_EVENT_PATH': 'event.json',
                                                       'DISCORD_FAILURE_USER_IDS': ','.join(RECIPIENTS)}), \
                    mock.patch.object(notify, 'workflow_jobs', return_value=[]), \
                    mock.patch.object(notify, 'deliver', return_value='555555555555555555') as send:
                if source_event == 'workflow_dispatch':
                    notify.main()
                    send.assert_called_once()
                else:
                    with self.assertRaises(notify.NotificationError):
                        notify.main()
                    send.assert_not_called()

    def test_untrusted_or_unrelated_runs_never_use_webhook(self):
        for changes in ({'head_branch': 'other'}, {'event': 'pull_request'},
                        {'head_repository': {'full_name': 'someone/fork'}}, {'name': 'Other Workflow'}):
            trigger = event()
            trigger['workflow_run'].update(name='Silora Daily', head_branch='main', event='schedule',
                                           head_repository={'full_name': 'SiloraOSS/silora-android'})
            trigger['workflow_run'].update(changes)
            with self.subTest(changes=changes), \
                    mock.patch.object(notify.Path, 'read_text', return_value=notify.json.dumps(trigger)), \
                    mock.patch.dict(notify.os.environ, {'GITHUB_EVENT_PATH': 'event.json'}), \
                    mock.patch.object(notify, 'deliver') as send:
                with self.assertRaises(notify.NotificationError):
                    notify.main()
                send.assert_not_called()

    def test_wrong_channel_never_posts(self):
        with mock.patch.object(notify, 'discord_request', return_value={'channel_id': '999', 'type': 1}) as request:
            with self.assertRaises(notify.NotificationError):
                notify.deliver(WEBHOOK, CHANNEL, notify.notification(event(), [], RECIPIENTS))
            self.assertEqual(request.call_count, 1)

    def test_delivery_reads_back_message_and_rejects_missing_pings(self):
        payload = notify.notification(event(conclusion='failure'), [], RECIPIENTS)
        message = {'id': '555555555555555555', 'channel_id': CHANNEL, 'webhook_id': '444444444444444444',
                   'content': payload['content'], 'embeds': payload['embeds'],
                   'mentions': [{'id': uid} for uid in RECIPIENTS]}
        webhook = {'id': message['webhook_id'], 'channel_id': CHANNEL, 'type': 1}
        with mock.patch.object(notify, 'discord_request', side_effect=[webhook, message, message]) as request:
            self.assertEqual(notify.deliver(WEBHOOK, CHANNEL, payload), message['id'])
            self.assertEqual(request.call_args_list[1].args[0], WEBHOOK + '?wait=true')
            self.assertEqual(request.call_args_list[2].args[0], WEBHOOK + '/messages/' + message['id'])
        with mock.patch.object(notify, 'discord_request', side_effect=[webhook, message, dict(message, mentions=[])]):
            with self.assertRaises(notify.NotificationError):
                notify.deliver(WEBHOOK, CHANNEL, payload)

    def test_send_timeout_is_not_retried(self):
        webhook = {'id': '444444444444444444', 'channel_id': CHANNEL, 'type': 1}
        with mock.patch.object(notify, 'discord_request', side_effect=[webhook, notify.NotificationError('uncertain')]) as request:
            with self.assertRaises(notify.NotificationError):
                notify.deliver(WEBHOOK, CHANNEL, notify.notification(event(), [], RECIPIENTS))
            self.assertEqual(request.call_count, 2)


if __name__ == '__main__':
    unittest.main()

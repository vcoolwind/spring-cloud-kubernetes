/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.netflix.loadbalancer.Server;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * the KubernetesServicesServerList description.
 *
 * @author wuzishu
 */
public class KubernetesServicesServerList extends KubernetesServerList {

	private static final Log LOG = LogFactory.getLog(KubernetesServicesServerList.class);

	/**
	 * Instantiates a new Kubernetes services server list.
	 * @param client the client
	 * @param properties the properties
	 */
	KubernetesServicesServerList(KubernetesClient client,
			KubernetesRibbonProperties properties) {
		super(client, properties);
	}

	/**
	 * Concat service fully qualified domain name.
	 * @param service Service model
	 * @return service FQDN
	 */
	private String concatServiceFQDN(Service service) {
		return String.format("%s.%s.svc.%s", service.getMetadata().getName(),
				StringUtils.isNotBlank(service.getMetadata().getNamespace())
						? service.getMetadata().getNamespace() : "default",
				this.getProperties().getClusterDomain());
	}

	@Override
	public List<Server> getUpdatedListOfServers() {
		List<Server> result = new ArrayList<>();
		Service service = StringUtils.isNotBlank(this.getNamespace())
				? this.getClient().services().inNamespace(this.getNamespace())
						.withName(this.getServiceId()).get()
				: this.getClient().services().withName(this.getServiceId()).get();

		if (getProperties().getAllNamespaces()) {
			if (service != null) {
				// use same namespaces service first
				LOG.info(String.format(
						"found service with same namespaces in ribbon in namespace [%s] for name [%s] and portName [%s]",
						this.getNamespace(), this.getServiceId(), this.getPortName()));
			}
			else {
				// find service in all namespaces when the service not found in same
				// namespaces
				List<Service> services = this.getClient().services().inAnyNamespace()
						.list().getItems().stream()
						.filter(serviceTmp -> serviceTmp.getMetadata().getName()
								.equals(this.getServiceId()))
						.collect(Collectors.toList());

				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("services: %s", services));
				}
				// LOG.info(String.format("services in all namespaces: %s", services));
				// prevent from the same service ids in different namespaces
				if (services.size() > 1) {
					LOG.error(String.format(
							"Communication disabled due to non unique service id [%s] across all namespaces",
							this.getServiceId()));
					return null;
				}
				service = services.size() > 0 ? services.get(0) : null;
			}

		}

		if (service != null) {
			if (LOG.isDebugEnabled()) {
				LOG.info("Found Service[" + service.getMetadata().getName() + " -- "
						+ service.getMetadata().getName() + "]");
			}

			if (service.getSpec().getPorts().size() == 1) {
				result.add(new Server(this.concatServiceFQDN(service),
						service.getSpec().getPorts().get(0).getPort()));
			}
			else {
				for (ServicePort servicePort : service.getSpec().getPorts()) {
					if (Utils.isNotNullOrEmpty(this.getPortName())
							|| this.getPortName().endsWith(servicePort.getName())) {
						result.add(new Server(concatServiceFQDN(service),
								servicePort.getPort()));
					}
				}
			}
		}
		if (result.isEmpty()) {
			LOG.error(String.format(
					"Did not find any service in ribbon in namespace [%s] for name [%s] and portName [%s]",
					getProperties().getAllNamespaces() ? "ALL" : this.getNamespace(),
					this.getServiceId(), this.getPortName()));
		}
		return result;
	}

}

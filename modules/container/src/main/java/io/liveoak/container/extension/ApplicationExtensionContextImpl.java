package io.liveoak.container.extension;

import java.util.Properties;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.liveoak.container.tenancy.ApplicationConfigurationManager;
import io.liveoak.container.tenancy.InternalApplicationExtension;
import io.liveoak.container.tenancy.MountPointResource;
import io.liveoak.spi.Application;
import io.liveoak.spi.LiveOak;
import io.liveoak.spi.MediaType;
import io.liveoak.spi.extension.ApplicationExtensionContext;
import io.liveoak.spi.resource.RootResource;
import io.liveoak.spi.resource.async.Resource;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
public class ApplicationExtensionContextImpl implements ApplicationExtensionContext {

    public ApplicationExtensionContextImpl(ServiceTarget target, InternalApplicationExtension appExtension, ServiceName publicMount, ServiceName adminMount, ObjectNode configuration, boolean boottime) {
        this.target = target;
        this.appExtension = appExtension;
        this.publicMount = publicMount;
        this.adminMount = adminMount;
        this.configuration = configuration;
        this.boottime = boottime;
    }

    public String extensionId() {
        return this.appExtension.extensionId();
    }

    public String resourceId() {
        return this.appExtension.resourceId();
    }

    @Override
    public Application application() {
        return this.appExtension.application();
    }

    @Override
    public ServiceTarget target() {
        return this.target;
    }

    @Override
    public void mountPublic() {
        mountPublic(LiveOak.resource(application().id(), resourceId()));
    }

    @Override
    public void mountPublic(ServiceName publicName) {
        mountPublic(publicName, null);
    }

    @Override
    public void mountPublic(ServiceName publicName, MediaType mediaType) {
        mountPublic(publicName, mediaType, false);
    }

    @Override
    public void mountPublic(ServiceName publicName, MediaType mediaType, boolean makeDefault) {
        MediaTypeMountService<RootResource> mount = new MediaTypeMountService(this.appExtension.resourceId(), mediaType, makeDefault);
        ServiceController<? extends Resource> controller = this.target.addService(LiveOak.defaultMount(publicName), mount)
                .addDependency(this.publicMount, MountPointResource.class, mount.mountPointInjector())
                .addDependency(publicName, RootResource.class, mount.resourceInjector())
                .install();
        this.appExtension.publicResourceController(controller);
    }

    @Override
    public void mountPublic(RootResource publicResource) {
        mountPublic(publicResource, null);
    }

    @Override
    public void mountPublic(RootResource publicResource, MediaType mediaType) {
        mountPublic(publicResource, mediaType, false);
    }

    @Override
    public void mountPublic(RootResource publicResource, MediaType mediaType, boolean makeDefault) {
        ValueService<RootResource> service = new ValueService<RootResource>(new ImmediateValue<>(publicResource));
        this.target.addService(LiveOak.resource(application().id(), resourceId()), service)
                .install();
        mountPublic(LiveOak.resource(application().id(), resourceId()), mediaType, makeDefault);
    }

    @Override
    public void mountPrivate() {
        mountPrivate(LiveOak.adminResource(application().id(), resourceId()));
    }

    @Override
    public void mountPrivate(ServiceName privateName) {
        mountPrivate(privateName, null);
    }

    @Override
    public void mountPrivate(ServiceName privateName, MediaType mediaType) {
        mountPrivate(privateName, mediaType, false);
    }

    @Override
    public void mountPrivate(ServiceName privateName, MediaType mediaType, boolean makeDefault) {
        MediaTypeMountService<RootResource> mount = new MediaTypeMountService(this.appExtension.resourceId(), mediaType, makeDefault);

        ConfigFilteringService configFilter = new ConfigFilteringService(filteringProperties());
        target.addService(privateName.append("filter-config"), configFilter)
                .addInjection(configFilter.configurationInjector(), this.configuration)
                .install();

        AdminResourceWrappingResourceService wrapper = new AdminResourceWrappingResourceService(this.appExtension, this.boottime);
        target.addService(privateName.append("wrapper"), wrapper)
                .addDependency(privateName.append("filter-config"))
                .addDependency(privateName, RootResource.class, wrapper.resourceInjector())
                .addDependency(LiveOak.applicationConfigurationManager(appExtension.application().id()), ApplicationConfigurationManager.class, wrapper.configurationManagerInjector())
                .install();

        UpdateResourceService configApply = new UpdateResourceService(this.appExtension);
        target.addService(privateName.append("apply-config"), configApply)
                .addDependency(privateName.append("wrapper"), Resource.class, configApply.resourceInjector())
                .addDependency(privateName.append("filter-config"), ObjectNode.class, configApply.configurationInjector())
                .install();

        RootResourceLifecycleService lifecycle = new RootResourceLifecycleService();
        target.addService(privateName.append("lifecycle"), lifecycle)
                .addDependency(privateName.append("apply-config"))
                .addDependency(privateName, RootResource.class, lifecycle.resourceInjector())
                .install();

        ServiceController<? extends Resource> controller = this.target.addService(LiveOak.defaultMount(privateName), mount)
                .addDependency(privateName.append("lifecycle"))
                .addDependency(this.adminMount, MountPointResource.class, mount.mountPointInjector())
                .addDependency(privateName.append("wrapper"), RootResource.class, mount.resourceInjector())
                .install();
        this.appExtension.adminResourceController(controller);
    }

    @Override
    public void mountPrivate(RootResource privateResource) {
        mountPrivate(privateResource, null);
    }

    @Override
    public void mountPrivate(RootResource privateResource, MediaType mediaType) {
        mountPrivate(privateResource, mediaType, false);
    }

    @Override
    public void mountPrivate(RootResource privateResource, MediaType mediaType, boolean makeDefault) {
        ValueService<RootResource> service = new ValueService<RootResource>(new ImmediateValue<>(privateResource));
        this.target.addService(LiveOak.adminResource(application().id(), resourceId()), service)
                .install();
        mountPrivate(LiveOak.adminResource(application().id(), resourceId()), mediaType, makeDefault);
    }

    protected Properties filteringProperties() {
        Properties props = new Properties();
        props.setProperty("application.id", application().id());
        props.setProperty("application.name", application().name());
        props.setProperty("application.dir", application().directory().getAbsolutePath());
        return props;
    }

    private ServiceTarget target;
    private final ServiceName publicMount;
    private final ServiceName adminMount;
    private final InternalApplicationExtension appExtension;
    private final ObjectNode configuration;
    private final boolean boottime;

}
